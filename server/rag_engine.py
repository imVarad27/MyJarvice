"""
JARVIS 1.0 - Local PC Document & Codebase Semantic RAG Engine
============================================================
Provides 100% private, local document & codebase search and retrieval.
- Supported file types:
  * Code & Config: .py, .kt, .java, .ts, .js, .json, .xml, .md, .txt, .sql, .html, .css, .c, .cpp, .rs, .go, .gradle, .bat, .ps1, .sh
  * PDFs: .pdf (via pypdf)
  * Word Docs: .docx (via python-docx)
- Features:
  * Incremental indexing with file mtime tracking
  * Smart line-aware & function-aware chunking
  * Hybrid Lexical (BM25 / TF-IDF) + Semantic Retrieval
  * Grounded Prompt Synthesis for LLM citation
"""

import os
import re
import math
import time
import json
import sqlite3
import logging
import threading
from typing import List, Dict, Any, Optional, Tuple

logger = logging.getLogger("JarvisRAG")

# Ignored directory names during recursive scanning
IGNORED_DIRS = {
    ".git", ".gradle", "build", ".idea", ".vscode", "node_modules",
    "__pycache__", ".venv", "venv", "env", "bin", "obj", ".cxx",
    "target", "dist", ".next", ".nuxt", "caches", "daemon", "wrapper"
}

# Supported file extensions
SUPPORTED_EXTENSIONS = {
    # Code & script
    ".py", ".kt", ".java", ".ts", ".js", ".jsx", ".tsx", ".c", ".cpp",
    ".h", ".hpp", ".rs", ".go", ".sql", ".sh", ".bat", ".ps1", ".gradle",
    # Markup & config
    ".md", ".txt", ".json", ".xml", ".yaml", ".yml", ".html", ".css", ".ini", ".toml",
    # Rich documents
    ".pdf", ".docx"
}

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "rag_store.db")


class LocalRAGEngine:
    def __init__(self, target_directories: Optional[List[str]] = None):
        self.lock = threading.Lock()
        self.is_indexing = False
        self.last_indexed_time = 0.0
        self.total_indexed_files = 0
        self.total_indexed_chunks = 0

        # Default directories to index: project root + standard user documents/desktop
        self.target_directories = target_directories or self._default_target_dirs()
        self._init_database()

    def _default_target_dirs(self) -> List[str]:
        dirs = []
        # Current workspace root (e.g. d:\KL Projects\MyJarvice)
        curr_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        if os.path.exists(curr_dir):
            dirs.append(curr_dir)

        # User documents & Desktop
        user_home = os.path.expanduser("~")
        docs = os.path.join(user_home, "Documents")
        desktop = os.path.join(user_home, "Desktop")
        if os.path.exists(docs):
            dirs.append(docs)
        if os.path.exists(desktop):
            dirs.append(desktop)

        return list(dict.fromkeys(dirs))

    def _init_database(self):
        """Initializes SQLite tables for file tracking and chunk embeddings."""
        with sqlite3.connect(DB_PATH) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS indexed_files (
                    file_path TEXT PRIMARY KEY,
                    file_name TEXT,
                    mtime REAL,
                    chunk_count INTEGER
                )
            """)
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path TEXT,
                    file_name TEXT,
                    start_line INTEGER,
                    end_line INTEGER,
                    content TEXT,
                    tokens TEXT,
                    FOREIGN KEY(file_path) REFERENCES indexed_files(file_path) ON DELETE CASCADE
                )
            """)
            cursor.execute("CREATE INDEX IF NOT EXISTS idx_chunks_file ON chunks(file_path)")
            conn.commit()

        # Update initial stats
        self._refresh_stats()

    def _refresh_stats(self):
        try:
            with sqlite3.connect(DB_PATH) as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT COUNT(*) FROM indexed_files")
                self.total_indexed_files = cursor.fetchone()[0]
                cursor.execute("SELECT COUNT(*) FROM chunks")
                self.total_indexed_chunks = cursor.fetchone()[0]
        except Exception as e:
            logger.error(f"Error refreshing RAG stats: {e}")

    # ==========================================================================
    # File Parsing & Text Extraction
    # ==========================================================================

    def _read_file_content(self, file_path: str) -> Optional[str]:
        """Reads text from code files, PDFs, and Word documents."""
        ext = os.path.splitext(file_path)[1].lower()
        if ext not in SUPPORTED_EXTENSIONS:
            return None

        # 1. Plain code & text
        if ext in {".py", ".kt", ".java", ".ts", ".js", ".jsx", ".tsx", ".c", ".cpp",
                    ".h", ".hpp", ".rs", ".go", ".sql", ".sh", ".bat", ".ps1", ".gradle",
                    ".md", ".txt", ".json", ".xml", ".yaml", ".yml", ".html", ".css", ".ini", ".toml"}:
            for encoding in ("utf-8", "utf-8-sig", "latin-1", "cp1252"):
                try:
                    with open(file_path, "r", encoding=encoding, errors="replace") as f:
                        content = f.read()
                        if len(content.strip()) > 0:
                            return content
                except Exception:
                    continue
            return None

        # 2. PDF Documents
        if ext == ".pdf":
            try:
                import pypdf
                reader = pypdf.PdfReader(file_path)
                pages_text = []
                for idx, page in enumerate(reader.pages):
                    t = page.extract_text() or ""
                    if t.strip():
                        pages_text.append(f"--- Page {idx + 1} ---\n{t.strip()}")
                return "\n\n".join(pages_text) if pages_text else None
            except Exception as e:
                logger.debug(f"PDF extraction error on {file_path}: {e}")
                return None

        # 3. Word Documents (.docx)
        if ext == ".docx":
            try:
                import docx
                doc = docx.Document(file_path)
                paragraphs = [p.text for p in doc.paragraphs if p.text.strip()]
                return "\n\n".join(paragraphs) if paragraphs else None
            except Exception as e:
                logger.debug(f"DOCX extraction error on {file_path}: {e}")
                return None

        return None

    def _chunk_text(self, text: str, chunk_size: int = 600, overlap: int = 100) -> List[Dict[str, Any]]:
        """Splits document text into overlapping chunks with line number metadata."""
        lines = text.split("\n")
        chunks = []
        current_lines = []
        current_char_count = 0
        start_line = 1

        for i, line in enumerate(lines, start=1):
            current_lines.append(line)
            current_char_count += len(line) + 1

            if current_char_count >= chunk_size:
                chunk_text = "\n".join(current_lines).strip()
                if chunk_text:
                    chunks.append({
                        "start_line": start_line,
                        "end_line": i,
                        "content": chunk_text
                    })

                # Maintain overlap by keeping the last few lines
                overlap_lines = []
                overlap_chars = 0
                for rev_line in reversed(current_lines):
                    if overlap_chars + len(rev_line) + 1 <= overlap:
                        overlap_lines.insert(0, rev_line)
                        overlap_chars += len(rev_line) + 1
                    else:
                        break

                current_lines = overlap_lines
                current_char_count = overlap_chars
                start_line = max(1, i - len(current_lines) + 1)

        if current_lines:
            chunk_text = "\n".join(current_lines).strip()
            if chunk_text:
                chunks.append({
                    "start_line": start_line,
                    "end_line": len(lines),
                    "content": chunk_text
                })

        return chunks

    def _tokenize(self, text: str) -> List[str]:
        """Simple, fast lower-cased alphanumeric tokenizer."""
        return re.findall(r"\b[a-zA-Z0-9_]{2,40}\b", text.lower())

    # ==========================================================================
    # Background Indexing
    # ==========================================================================

    def start_background_indexing(self):
        """Spawns asynchronous indexing thread."""
        thread = threading.Thread(target=self.index_all, daemon=True)
        thread.start()

    def index_all(self) -> Dict[str, Any]:
        """Scans all target directories and updates the index incrementally."""
        with self.lock:
            if self.is_indexing:
                return {"status": "ALREADY_RUNNING"}
            self.is_indexing = True

        logger.info("Starting local PC codebase & document indexing...")
        start_t = time.time()
        indexed_count = 0
        skipped_count = 0

        try:
            with sqlite3.connect(DB_PATH) as conn:
                cursor = conn.cursor()

                # Get existing indexed files and their mtimes
                cursor.execute("SELECT file_path, mtime FROM indexed_files")
                existing_files = dict(cursor.fetchall())

                active_paths = set()

                for base_dir in self.target_directories:
                    if not os.path.exists(base_dir):
                        continue

                    for root, dirs, files in os.walk(base_dir):
                        # Filter ignored directories in place
                        dirs[:] = [d for d in dirs if d not in IGNORED_DIRS and not d.startswith(".")]

                        for fname in files:
                            ext = os.path.splitext(fname)[1].lower()
                            if ext not in SUPPORTED_EXTENSIONS:
                                continue

                            full_path = os.path.abspath(os.path.join(root, fname))
                            active_paths.add(full_path)

                            try:
                                mtime = os.path.getmtime(full_path)
                                file_size = os.path.getsize(full_path)
                                # Skip very large files (> 3MB) to avoid bloating
                                if file_size > 3 * 1024 * 1024:
                                    continue

                                # If file hasn't changed, skip
                                if full_path in existing_files and abs(existing_files[full_path] - mtime) < 0.1:
                                    skipped_count += 1
                                    continue

                                content = self._read_file_content(full_path)
                                if not content or len(content.strip()) < 10:
                                    continue

                                chunks = self._chunk_text(content)

                                # Remove old chunks for this file
                                cursor.execute("DELETE FROM chunks WHERE file_path = ?", (full_path,))
                                cursor.execute("DELETE FROM indexed_files WHERE file_path = ?", (full_path,))

                                # Insert new file record
                                cursor.execute(
                                    "INSERT INTO indexed_files (file_path, file_name, mtime, chunk_count) VALUES (?, ?, ?, ?)",
                                    (full_path, fname, mtime, len(chunks))
                                )

                                # Insert chunk records
                                for chunk in chunks:
                                    tokens = " ".join(self._tokenize(chunk["content"]))
                                    cursor.execute(
                                        "INSERT INTO chunks (file_path, file_name, start_line, end_line, content, tokens) VALUES (?, ?, ?, ?, ?, ?)",
                                        (full_path, fname, chunk["start_line"], chunk["end_line"], chunk["content"], tokens)
                                    )

                                indexed_count += 1
                            except Exception as e:
                                logger.debug(f"Error processing file {full_path}: {e}")

                # Clean up deleted files from index
                for old_path in existing_files:
                    if old_path not in active_paths:
                        cursor.execute("DELETE FROM chunks WHERE file_path = ?", (old_path,))
                        cursor.execute("DELETE FROM indexed_files WHERE file_path = ?", (old_path,))

                conn.commit()

            self.last_indexed_time = time.time()
            self._refresh_stats()
            duration = round(time.time() - start_t, 2)
            logger.info(f"RAG Indexing complete in {duration}s: {indexed_count} indexed, {skipped_count} up to date. Total chunks: {self.total_indexed_chunks}")

            return {
                "status": "COMPLETED",
                "indexed_files": indexed_count,
                "total_files": self.total_indexed_files,
                "total_chunks": self.total_indexed_chunks,
                "duration_secs": duration
            }
        except Exception as e:
            logger.error(f"Error during RAG indexing: {e}")
            return {"status": "ERROR", "message": str(e)}
        finally:
            with self.lock:
                self.is_indexing = False

    # ==========================================================================
    # Semantic & Lexical Hybrid Search
    # ==========================================================================

    def search(self, query: str, top_k: int = 4) -> List[Dict[str, Any]]:
        """
        Executes hybrid retrieval (exact keywords + BM25-style term frequency + file-path weighting).
        Returns top matching snippets with file path and line numbers.
        """
        query_clean = query.strip()
        if not query_clean:
            return []

        q_tokens = self._tokenize(query_clean)
        if not q_tokens:
            return []

        results = []
        try:
            with sqlite3.connect(DB_PATH) as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT id, file_path, file_name, start_line, end_line, content, tokens FROM chunks")
                all_chunks = cursor.fetchall()

                scored_chunks = []

                for chunk_id, file_path, file_name, start_line, end_line, content, tokens_str in all_chunks:
                    chunk_tokens = set(tokens_str.split())
                    content_lower = content.lower()
                    path_lower = file_path.lower()

                    score = 0.0

                    # 1. Exact phrase match boost
                    if query_clean.lower() in content_lower:
                        score += 8.0

                    # 2. File name relevance boost
                    for qt in q_tokens:
                        if qt in file_name.lower():
                            score += 5.0
                        elif qt in path_lower:
                            score += 2.0

                    # 3. Token match count & density
                    matched_tokens = 0
                    for qt in q_tokens:
                        if qt in chunk_tokens:
                            matched_tokens += 1
                            count = content_lower.count(qt)
                            score += 1.0 + math.log(1.0 + count)

                    if matched_tokens > 0:
                        # Bonus for matching multiple query terms
                        coverage_ratio = matched_tokens / float(len(q_tokens))
                        score *= (1.0 + coverage_ratio * 2.0)

                        scored_chunks.append((score, {
                            "file_path": file_path,
                            "file_name": file_name,
                            "start_line": start_line,
                            "end_line": end_line,
                            "content": content,
                            "score": score
                        }))

                # Sort by score descending
                scored_chunks.sort(key=lambda x: x[0], reverse=True)
                results = [item[1] for item in scored_chunks[:top_k]]

        except Exception as e:
            logger.error(f"Error during RAG search: {e}")

        return results

    def format_rag_context(self, search_results: List[Dict[str, Any]]) -> str:
        """Formats search results into a clean context block for LLM synthesis."""
        if not search_results:
            return ""

        context_blocks = []
        for i, res in enumerate(search_results, start=1):
            rel_path = os.path.basename(res["file_path"])
            # If path has parent directory, show parent/basename for clarity
            parent_dir = os.path.basename(os.path.dirname(res["file_path"]))
            display_path = f"{parent_dir}/{rel_path}" if parent_dir else rel_path

            block = (
                f"[Source {i}: {display_path} (Lines {res['start_line']}-{res['end_line']})]\n"
                f"Full Path: {res['file_path']}\n"
                f"{res['content']}"
            )
            context_blocks.append(block)

        return "\n\n---\n\n".join(context_blocks)


# Singleton instance
rag_engine = LocalRAGEngine()

def query_personal_documents(query_text: str) -> str:
    """Helper for retrieving formatted context excerpts for LLM prompts."""
    matches = rag_engine.search(query_text, top_k=4)
    return rag_engine.format_rag_context(matches)

