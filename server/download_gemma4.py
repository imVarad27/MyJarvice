import os
import sys
from huggingface_hub import hf_hub_download

# Check if E:\ drive exists, otherwise default to D:\MyJarviceModels (260GB free)
if os.path.exists("E:\\"):
    TARGET_DIR = r"E:\MyJarviceModels"
else:
    TARGET_DIR = r"D:\MyJarviceModels"

REPO_ID = "google/gemma-4-E4B-it-qat-q4_0-gguf"
FILENAME = "gemma-4-E4B_q4_0-it.gguf"

os.makedirs(TARGET_DIR, exist_ok=True)
dest_path = os.path.join(TARGET_DIR, FILENAME)

print(f"Target Directory: {TARGET_DIR}")
print(f"Downloading {FILENAME} from Hugging Face ({REPO_ID})...")

try:
    downloaded_file = hf_hub_download(
        repo_id=REPO_ID,
        filename=FILENAME,
        local_dir=TARGET_DIR
    )
    print(f"\n✅ Model successfully downloaded to: {downloaded_file}")
except Exception as e:
    print(f"\n❌ Download failed: {e}")
