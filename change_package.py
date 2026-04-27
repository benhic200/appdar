#!/usr/bin/env python3
"""
Script to change package name from com.example.nearbyappswidget to com.benhic.appdar
Run from the project root directory.
eg 
cd /root/.openclaw/Adroid_Dev/nearby-apps-widget/phase1
python3 scripts/dataset_editor.py
"""

import os
import re
import shutil
from pathlib import Path

OLD_PACKAGE = "com.example.nearbyappswidget"
NEW_PACKAGE = "com.benhic.appdar"

def replace_in_file(filepath, old, new):
    """Replace old string with new in a file."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        if old in content:
            content = content.replace(old, new)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"Updated {filepath}")
            return True
    except Exception as e:
        print(f"Error processing {filepath}: {e}")
    return False

def rename_directories(root_dir):
    """Rename com/example/nearbyappswidget to com/benhic/appdar under kotlin/java source directories."""
    for src_type in ['kotlin', 'java']:
        src_dir = Path(root_dir) / 'src' / 'main' / src_type
        if not src_dir.exists():
            continue
        # Walk from src_dir down to find com/example/nearbyappswidget
        for path in src_dir.rglob('*'):
            if path.is_dir() and path.name == 'nearbyappswidget' and 'com/example/nearbyappswidget' in str(path):
                # Check if parent is example, and its parent is com
                if path.parent.name == 'example' and path.parent.parent.name == 'com':
                    # Build new path: replace example with benhic, nearbyappswidget with appdar
                    new_path = path.parent.parent / 'benhic' / 'appdar'
                    # Ensure parent directories exist
                    new_path.parent.mkdir(parents=True, exist_ok=True)
                    # Move the directory
                    shutil.move(str(path), str(new_path))
                    print(f"Moved {path} -> {new_path}")
                    # Remove empty ancestor directories if they're now empty
                    old_example_dir = path.parent
                    old_com_dir = old_example_dir.parent
                    try:
                        if old_example_dir.exists() and not any(old_example_dir.iterdir()):
                            old_example_dir.rmdir()
                            print(f"Removed empty directory {old_example_dir}")
                        if old_com_dir.exists() and not any(old_com_dir.iterdir()):
                            old_com_dir.rmdir()
                            print(f"Removed empty directory {old_com_dir}")
                    except OSError:
                        pass

def process_module(module_path):
    """Process a single module directory."""
    module_path = Path(module_path)
    print(f"\n=== Processing module: {module_path.name} ===")
    
    # 1. Update build.gradle.kts namespace
    gradle_file = module_path / "build.gradle.kts"
    if gradle_file.exists():
        replace_in_file(gradle_file, OLD_PACKAGE, NEW_PACKAGE)
    
    # 2. Update AndroidManifest.xml package attribute (if present)
    manifest_file = module_path / "src/main/AndroidManifest.xml"
    if manifest_file.exists():
        replace_in_file(manifest_file, OLD_PACKAGE, NEW_PACKAGE)
    
    # 3. Update all .kt and .java files (package statements, imports, etc.)
    for ext in ['*.kt', '*.java']:
        for file in module_path.rglob(ext):
            if file.is_file():
                replace_in_file(file, OLD_PACKAGE, NEW_PACKAGE)
    
    # 4. Update all .xml files (resource files, layout files)
    for file in module_path.rglob('*.xml'):
        if file.is_file():
            replace_in_file(file, OLD_PACKAGE, NEW_PACKAGE)
    
    # 5. Rename directories
    rename_directories(module_path)

def main():
    project_root = Path.cwd()
    print(f"Project root: {project_root}")
    
    # List modules from settings.gradle.kts (simplified)
    modules = [
        "app", "core", "data", "domain", "feature-geofencing",
        "feature-location", "feature-widget", "feature-widget-list",
        "feature-settings", "feature-geocoding"
    ]
    
    for module in modules:
        module_path = project_root / module
        if module_path.exists():
            process_module(module_path)
        else:
            print(f"Module {module} not found at {module_path}")
    
    # Also update root build.gradle.kts and settings.gradle.kts
    root_files = ["build.gradle.kts", "settings.gradle.kts"]
    for f in root_files:
        filepath = project_root / f
        if filepath.exists():
            replace_in_file(filepath, OLD_PACKAGE, NEW_PACKAGE)
    
    # Update gradle.properties if any reference
    gradle_props = project_root / "gradle.properties"
    if gradle_props.exists():
        replace_in_file(gradle_props, OLD_PACKAGE, NEW_PACKAGE)
    
    # Update local.properties? Probably not needed.
    
    print("\n=== Package rename completed ===")

if __name__ == "__main__":
    main()