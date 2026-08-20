import os
import glob
import logging
from typing import List, Dict
import pypdf

logger = logging.getLogger("JarviceRAG")

DOCUMENTS_DIR = os.path.join(os.path.dirname(__file__), "documents")

class DocumentRAGEngine:
    def __init__(self, doc_dir: str = DOCUMENTS_DIR):
        self.doc_dir = doc_dir
        self.chunks: List[Dict[str, str]] = []
        self.reload_documents()

    def reload_documents(self):
        """Scans the documents directory and indexes all PDF, MD, and TXT files."""
        self.chunks.clear()
        if not os.path.exists(self.doc_dir):
            os.makedirs(self.doc_dir, exist_ok=True)
            return

        supported_files = glob.glob(os.path.join(self.doc_dir, "**/*.*"), recursive=True)
        for filepath in supported_files:
            filename = os.path.basename(filepath)
            ext = os.path.splitext(filename)[1].lower()
            text = ""

            try:
                if ext == ".pdf":
                    reader = pypdf.PdfReader(filepath)
                    for page in reader.pages:
                        extracted = page.extract_text()
                        if extracted:
                            text += extracted + "\n"
                elif ext in [".txt", ".md", ".json", ".csv"]:
                    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                        text = f.read()

                if text.strip():
                    self._split_and_index(filename, text)
            except Exception as e:
                logger.error(f"Error reading document {filename}: {e}")

        logger.info(f"RAG Engine loaded {len(self.chunks)} document chunks from {self.doc_dir}")

    def _split_and_index(self, filename: str, text: str, chunk_size: int = 400, overlap: int = 50):
        words = text.split()
        for i in range(0, len(words), chunk_size - overlap):
            chunk_text = " ".join(words[i:i + chunk_size])
            if chunk_text.strip():
                self.chunks.append({
                    "source": filename,
                    "text": chunk_text
                })

    def search(self, query: str, top_k: int = 3) -> str:
        """Simple TF-IDF / Keyword match ranker for ultra-fast local retrieval."""
        if not self.chunks:
            return "No personal documents found in the server/documents directory."

        query_terms = [w.lower() for w in query.split() if len(w) > 2]
        scored_chunks = []

        for chunk in self.chunks:
            chunk_lower = chunk["text"].lower()
            score = sum(chunk_lower.count(term) for term in query_terms)
            if score > 0:
                scored_chunks.append((score, chunk))

        scored_chunks.sort(key=lambda x: x[0], reverse=True)
        results = scored_chunks[:top_k]

        if not results:
            # Fallback to top 2 chunks if keyword exact matches fail
            results = [(1, c) for c in self.chunks[:2]]

        formatted = []
        for rank, (score, chunk) in enumerate(results, 1):
            formatted.append(f"--- Document [{chunk['source']}] ---\n{chunk['text']}")

        return "\n\n".join(formatted)

# Global singleton RAG instance
rag_engine = DocumentRAGEngine()

def query_personal_documents(query: str) -> str:
    """Queries personal PDFs and documents stored in server/documents/."""
    return rag_engine.search(query)
