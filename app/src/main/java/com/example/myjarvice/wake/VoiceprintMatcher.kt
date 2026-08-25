package com.example.myjarvice.wake

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-Device Speaker Verification & Voiceprint Biometric Engine.
 *
 * Implements a high-precision acoustic feature extraction pipeline:
 * 1. Pre-emphasis filtering (high-frequency boost)
 * 2. Hamming windowing & STFT framing (25ms window, 10ms stride at 16kHz)
 * 3. 40-channel Mel Filterbank log-energy computation
 * 4. Discrete Cosine Transform (MFCC) & temporal delta features
 * 5. Statistical pooling (temporal moments & spectral centroid) yielding a 192-dim normalized embedding
 * 6. Cosine Similarity verification against the enrolled Master Voiceprint
 */
object VoiceprintMatcher {

    const val SAMPLE_RATE = 16000
    private const val FRAME_LEN = 400      // 25ms at 16kHz
    private const val FRAME_SHIFT = 160    // 10ms at 16kHz
    private const val FFT_SIZE = 512       // Next power of 2 for 400
    private const val NUM_MEL_FILTERS = 40
    private const val NUM_CEPSTRAL = 24
    const val EMBEDDING_DIM = 192

    private val hammingWindow: FloatArray by lazy {
        FloatArray(FRAME_LEN) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_LEN - 1))).toFloat()
        }
    }

    private val melFilterBank: Array<FloatArray> by lazy {
        buildMelFilterBank(SAMPLE_RATE, FFT_SIZE, NUM_MEL_FILTERS, 80.0, 7600.0)
    }

    /**
     * Extracts a normalized 192-dimensional acoustic speaker embedding from 16kHz PCM audio.
     */
    fun computeEmbedding(pcmSamples: ShortArray): FloatArray {
        if (pcmSamples.size < FRAME_LEN) {
            return FloatArray(EMBEDDING_DIM)
        }

        // Convert PCM shorts to normalized floats in [-1.0, 1.0] and apply pre-emphasis
        val floatAudio = FloatArray(pcmSamples.size)
        val alpha = 0.97f
        floatAudio[0] = pcmSamples[0] / 32768.0f
        for (i in 1 until pcmSamples.size) {
            val curr = pcmSamples[i] / 32768.0f
            val prev = pcmSamples[i - 1] / 32768.0f
            floatAudio[i] = curr - alpha * prev
        }

        val numFrames = max(1, (floatAudio.size - FRAME_LEN) / FRAME_SHIFT + 1)
        val mfccFrames = Array(numFrames) { FloatArray(NUM_CEPSTRAL) }
        val melEnergyFrames = Array(numFrames) { FloatArray(NUM_MEL_FILTERS) }
        val spectralCentroids = FloatArray(numFrames)
        val frameEnergies = FloatArray(numFrames)
        val subbandAccum = FloatArray(4)

        val fftReal = FloatArray(FFT_SIZE)
        val fftImag = FloatArray(FFT_SIZE)
        val powerSpectrum = FloatArray(FFT_SIZE / 2 + 1)

        for (frameIdx in 0 until numFrames) {
            val offset = frameIdx * FRAME_SHIFT
            for (i in 0 until FFT_SIZE) {
                if (i < FRAME_LEN && (offset + i) < floatAudio.size) {
                    fftReal[i] = floatAudio[offset + i] * hammingWindow[i]
                } else {
                    fftReal[i] = 0.0f
                }
                fftImag[i] = 0.0f
            }

            // In-place Radix-2 FFT
            computeFFT(fftReal, fftImag)

            // Power spectrum & spectral centroid
            var totalPower = 0.0f
            var weightedFreqSum = 0.0f
            for (k in 0..FFT_SIZE / 2) {
                val mag = fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k]
                powerSpectrum[k] = mag
                totalPower += mag
                weightedFreqSum += k * mag
            }

            frameEnergies[frameIdx] = ln(max(1e-10f, totalPower))
            spectralCentroids[frameIdx] = if (totalPower > 1e-6f) (weightedFreqSum / totalPower) else 0.0f

            // Subband energy distribution
            val numBinsPerBand = (FFT_SIZE / 2) / 4
            for (band in 0 until 4) {
                var bandSum = 0.0f
                val start = band * numBinsPerBand
                val end = (band + 1) * numBinsPerBand
                for (k in start until end) bandSum += powerSpectrum[k]
                subbandAccum[band] += bandSum
            }

            // Mel Filterbank Energies
            for (m in 0 until NUM_MEL_FILTERS) {
                var energy = 0.0f
                val filter = melFilterBank[m]
                for (k in 0..FFT_SIZE / 2) {
                    energy += powerSpectrum[k] * filter[k]
                }
                val logE = ln(max(1e-10f, energy))
                melEnergyFrames[frameIdx][m] = logE
            }

            // DCT with Cepstral Liftering to compute MFCC coefficients
            for (c in 0 until NUM_CEPSTRAL) {
                var sum = 0.0
                for (m in 0 until NUM_MEL_FILTERS) {
                    sum += melEnergyFrames[frameIdx][m] * cos(PI * c * (m + 0.5) / NUM_MEL_FILTERS)
                }
                // Cepstral lifter emphasizing formant resonances over DC gain
                val lifter = 1.0 + 11.0 * sin(PI * (c + 1) / NUM_CEPSTRAL)
                mfccFrames[frameIdx][c] = (sum * lifter).toFloat()
            }
        }


        // --- Temporal Statistical Pooling & Feature Moments (Exactly 192 dimensions) ---
        val embedding = FloatArray(EMBEDDING_DIM)
        var embIdx = 0

        // 1. Mean and Standard Deviation of MFCC coefficients (24 + 24 = 48 values)
        for (c in 0 until NUM_CEPSTRAL) {
            var mean = 0.0f
            for (f in 0 until numFrames) mean += mfccFrames[f][c]
            mean /= numFrames
            embedding[embIdx++] = mean

            var variance = 0.0f
            for (f in 0 until numFrames) {
                val diff = mfccFrames[f][c] - mean
                variance += diff * diff
            }
            embedding[embIdx++] = sqrt(variance / numFrames)
        }

        // 2. First-order temporal Deltas: Mean & Std (24 + 24 = 48 values)
        for (c in 0 until NUM_CEPSTRAL) {
            var deltaMean = 0.0f
            val deltas = FloatArray(numFrames)
            for (f in 0 until numFrames) {
                val prev = if (f > 0) mfccFrames[f - 1][c] else mfccFrames[f][c]
                val next = if (f < numFrames - 1) mfccFrames[f + 1][c] else mfccFrames[f][c]
                deltas[f] = (next - prev) * 0.5f
                deltaMean += deltas[f]
            }
            deltaMean /= numFrames
            embedding[embIdx++] = deltaMean

            var deltaVar = 0.0f
            for (f in 0 until numFrames) {
                val diff = deltas[f] - deltaMean
                deltaVar += diff * diff
            }
            embedding[embIdx++] = sqrt(deltaVar / numFrames)
        }

        // 3. Second-order Delta-Deltas: Mean & Std (24 + 24 = 48 values)
        for (c in 0 until NUM_CEPSTRAL) {
            var d2Mean = 0.0f
            for (f in 1 until numFrames - 1) {
                val d2 = mfccFrames[f + 1][c] - 2.0f * mfccFrames[f][c] + mfccFrames[f - 1][c]
                d2Mean += d2
            }
            d2Mean /= max(1, numFrames - 2)
            embedding[embIdx++] = d2Mean

            var d2Var = 0.0f
            for (f in 1 until numFrames - 1) {
                val d2 = mfccFrames[f + 1][c] - 2.0f * mfccFrames[f][c] + mfccFrames[f - 1][c]
                val diff = d2 - d2Mean
                d2Var += diff * diff
            }
            embedding[embIdx++] = sqrt(d2Var / max(1, numFrames - 2))
        }

        // 4. Mean Mel Filterbank log-energies across frames (40 values)
        for (m in 0 until NUM_MEL_FILTERS) {
            var melMean = 0.0f
            for (f in 0 until numFrames) melMean += melEnergyFrames[f][m]
            melMean /= numFrames
            embedding[embIdx++] = melMean
        }

        // 5. Global energy & spectral dynamics (8 values)
        var scMean = 0.0f
        for (f in 0 until numFrames) scMean += spectralCentroids[f]
        scMean /= numFrames
        embedding[embIdx++] = scMean

        var scVar = 0.0f
        for (f in 0 until numFrames) {
            val diff = spectralCentroids[f] - scMean
            scVar += diff * diff
        }
        embedding[embIdx++] = sqrt(scVar / numFrames)

        var energyMean = 0.0f
        for (f in 0 until numFrames) energyMean += frameEnergies[f]
        energyMean /= numFrames
        embedding[embIdx++] = energyMean

        var energyVar = 0.0f
        for (f in 0 until numFrames) {
            val diff = frameEnergies[f] - energyMean
            energyVar += diff * diff
        }
        embedding[embIdx++] = sqrt(energyVar / numFrames)

        // 4 subband normalized energy ratios
        var totalSubband = 0.0f
        for (b in 0 until 4) totalSubband += subbandAccum[b]
        for (b in 0 until 4) {
            val ratio = if (totalSubband > 1e-6f) (subbandAccum[b] / totalSubband) else 0.25f
            embedding[embIdx++] = ratio
        }

        // Final L2-normalization
        normalizeL2(embedding)
        return embedding
    }


    /**
     * Enrolls multiple voice training samples into a single robust Master Voiceprint.
     */
    fun enrollMasterProfile(samples: List<ShortArray>): FloatArray {
        if (samples.isEmpty()) return FloatArray(EMBEDDING_DIM)

        val master = FloatArray(EMBEDDING_DIM)
        var validCount = 0

        for (sample in samples) {
            if (sample.size >= FRAME_LEN) {
                val emb = computeEmbedding(sample)
                for (i in 0 until EMBEDDING_DIM) {
                    master[i] += emb[i]
                }
                validCount++
            }
        }

        if (validCount > 0) {
            for (i in 0 until EMBEDDING_DIM) {
                master[i] /= validCount
            }
            normalizeL2(master)
        }
        return master
    }

    /**
     * Calculates the Cosine Similarity between live voice embedding and the enrolled master voiceprint.
     * Returns a float value in [-1.0, 1.0], where 1.0 is an exact acoustic match.
     */
    fun calculateSimilarity(master: FloatArray, live: FloatArray): Float {
        if (master.size != live.size || master.isEmpty()) return 0.0f

        var dot = 0.0f
        var normMaster = 0.0f
        var normLive = 0.0f

        for (i in master.indices) {
            dot += master[i] * live[i]
            normMaster += master[i] * master[i]
            normLive += live[i] * live[i]
        }

        val denominator = sqrt(normMaster) * sqrt(normLive)
        return if (denominator > 1e-12f) (dot / denominator) else 0.0f
    }

    /**
     * Verifies live candidate audio against the enrolled master profile.
     */
    fun verify(master: FloatArray, liveAudio: ShortArray, threshold: Float = 0.72f): Pair<Boolean, Float> {
        val liveEmbedding = computeEmbedding(liveAudio)
        val score = calculateSimilarity(master, liveEmbedding)
        return Pair(score >= threshold, score)
    }

    private fun normalizeL2(vec: FloatArray) {
        if (vec.isEmpty()) return
        var sum = 0.0f
        for (v in vec) sum += v
        val mean = sum / vec.size
        for (i in vec.indices) {
            vec[i] -= mean
        }

        var sumSq = 0.0f
        for (v in vec) sumSq += v * v
        val norm = sqrt(sumSq)
        if (norm > 1e-12f) {
            for (i in vec.indices) {
                vec[i] /= norm
            }
        }
    }


    private fun buildMelFilterBank(
        sampleRate: Int,
        fftSize: Int,
        numFilters: Int,
        lowFreq: Double,
        highFreq: Double
    ): Array<FloatArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

        val lowMel = hzToMel(lowFreq)
        val highMel = hzToMel(highFreq)
        val melPoints = DoubleArray(numFilters + 2) { i ->
            lowMel + i * (highMel - lowMel) / (numFilters + 1)
        }

        val binPoints = IntArray(numFilters + 2) { i ->
            val hz = melToHz(melPoints[i])
            min(fftSize / 2, max(0, ((fftSize + 1) * hz / sampleRate).toInt()))
        }

        return Array(numFilters) { m ->
            val filter = FloatArray(fftSize / 2 + 1)
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]

            for (k in left until center) {
                if (center != left) {
                    filter[k] = ((k - left).toFloat() / (center - left))
                }
            }
            for (k in center until right) {
                if (right != center) {
                    filter[k] = ((right - k).toFloat() / (right - center))
                }
            }
            filter
        }
    }

    private fun computeFFT(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey Radix-2 FFT
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wR - imag[i + k + half] * wI
                    val vI = real[i + k + half] * wI + imag[i + k + half] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
