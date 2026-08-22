import os
import glob
import logging
from typing import List, Dict
import pypdf

logger = logging.getLogger("JarviceRAG")

DOCUMENTS_DIR = os.path.join(os.path.dirname(__file__), "documents")

class DocumentRAGEngine:
    def __init__(self, watch_dirs: List[str] = None):
        if watch_dirs is None:
            watch_dirs = [DOCUMENTS_DIR, "D:\\", "E:\\"]
        self.watch_dirs = watch_dirs
        self.chunks: List[Dict[str, str]] = []
        self.reload_documents()

    def reload_documents(self):
        r"""Scans entire D:\ drive and E:\ drive (if present) and indexes documents."""
        self.chunks.clear()
        ignored_dirs = {
            "node_modules", ".git", ".venv", "venv", "__pycache__", "build", 
            ".gradle", ".idea", "bin", "obj", ".gemini", "$RECYCLE.BIN", 
            "System Volume Information", "MyJarviceModels", "AndroidStudio", "AppData"
        }

        for target_dir in self.watch_dirs:
            if not os.path.exists(target_dir):
                logger.info(f"RAG watch directory '{target_dir}' does not exist, skipping.")
                continue

            logger.info(f"RAG indexing directory: {target_dir}")
            for root, dirs, files in os.walk(target_dir):
                # Filter out heavy developer directories
                dirs[:] = [d for d in dirs if d not in ignored_dirs and not d.startswith(".")]

                for filename in files:
                    ext = os.path.splitext(filename)[1].lower()
                    if ext not in [".pdf", ".txt", ".md", ".json", ".csv", ".py", ".kt", ".cs", ".js", ".ts"]:
                        continue

                    filepath = os.path.join(root, filename)
                    # Limit individual file size to 2MB to prevent memory bloat
                    try:
                        if os.path.getsize(filepath) > 2 * 1024 * 1024:
                            continue

                        text = ""
                        if ext == ".pdf":
                            reader = pypdf.PdfReader(filepath)
                            for page in reader.pages:
                                extracted = page.extract_text()
                                if extracted:
                                    text += extracted + "\n"
                        else:
                            with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                                text = f.read()

                        if text.strip():
                            rel_path = os.path.relpath(filepath, target_dir)
                            self._split_and_index(f"{os.path.basename(target_dir)}/{rel_path}", text)
                    except Exception as e:
                        logger.error(f"Error reading document {filename}: {e}")

        logger.info(f"RAG Engine loaded {len(self.chunks)} document chunks.")

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
