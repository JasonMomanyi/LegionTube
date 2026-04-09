"""
LegionTube Rebrand Script
Performs mass find-and-replace across the entire project.
"""
import os
import re

BASE_DIR = r"c:\Users\Administrator\Documents\Coding Projects\LS-TUBE"

# File extensions to process
TARGET_EXTENSIONS = {'.kt', '.xml', '.kts', '.properties', '.md', '.json', '.txt', '.pro', '.cfg'}

# Directories to skip
SKIP_DIRS = {'.git', '.gradle', 'build', '.idea', 'node_modules', '__pycache__'}

# Replacements in order (most specific first)
REPLACEMENTS = [
    # Package names (case-sensitive)
    ('com.github.libretube', 'com.github.legiontube'),
    ('com/github/libretube', 'com/github/legiontube'),
    
    # Class/brand references
    ('LibreTubeApp', 'LegionTubeApp'),
    ('LibreTube Debug', 'LegionTube Debug'),
    ('LibreTube', 'LegionTube'),
    ('libretube', 'legiontube'),
    ('LIBRETUBE', 'LEGIONTUBE'),
    ('Libre Tube', 'Legion Tube'),
    
    # Also fix LS-TUBE references from previous rebrand
    ('LS-TUBE Debug', 'LegionTube Debug'),
    ('LS-TUBE', 'LegionTube'),
    ('LS_TUBE', 'LegionTube'),
    ('lstube', 'legiontube'),
    ('com.lordstunnis.lstube', 'com.github.legiontube'),
]

def should_process(filepath):
    """Check if file should be processed."""
    _, ext = os.path.splitext(filepath)
    return ext.lower() in TARGET_EXTENSIONS

def process_file(filepath):
    """Replace strings in a single file."""
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
    except Exception as e:
        print(f"  SKIP (read error): {filepath} - {e}")
        return False

    original = content
    for old, new in REPLACEMENTS:
        content = content.replace(old, new)
    
    if content != original:
        try:
            with open(filepath, 'w', encoding='utf-8', newline='') as f:
                f.write(content)
            return True
        except Exception as e:
            print(f"  ERROR (write): {filepath} - {e}")
            return False
    return False

def main():
    # Process the NEW legiontube directory (not the old libretube one)
    processed = 0
    modified = 0
    
    for root, dirs, files in os.walk(BASE_DIR):
        # Skip excluded directories
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        
        # Skip the OLD libretube directory (we'll delete it after)
        rel = os.path.relpath(root, BASE_DIR)
        if 'libretube' in rel.split(os.sep) and 'legiontube' not in rel.split(os.sep):
            # Only skip if there's a parallel legiontube dir (meaning this is the old copy)
            parallel = root.replace('libretube', 'legiontube')
            if os.path.exists(parallel):
                continue
        
        for filename in files:
            filepath = os.path.join(root, filename)
            if should_process(filepath):
                processed += 1
                if process_file(filepath):
                    modified += 1
                    relpath = os.path.relpath(filepath, BASE_DIR)
                    print(f"  MODIFIED: {relpath}")
    
    print(f"\nProcessed {processed} files, modified {modified} files.")

if __name__ == "__main__":
    main()
