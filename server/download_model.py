import os
import sys
from huggingface_hub import hf_hub_download

TARGET_DIR = r"D:\MyJarviceModels"
REPO_ID = "bartowski/gemma-2-2b-it-GGUF"
FILENAME = "gemma-2-2b-it-Q4_K_M.gguf"

os.makedirs(TARGET_DIR, exist_ok=True)
dest_path = os.path.join(TARGET_DIR, FILENAME)

print(f"Downloading {FILENAME} from Hugging Face ({REPO_ID}) to {dest_path}...")

downloaded_file = hf_hub_download(
    repo_id=REPO_ID,
    filename=FILENAME,
    local_dir=TARGET_DIR,
    local_dir_use_symlinks=False
)

print(f"\n✅ Model successfully downloaded to: {downloaded_file}")
