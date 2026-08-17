import os
import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Regex to find if Loader2 is imported from lucide-react
    if re.search(r'import\s+\{[^\}]*\bLoader2\b[^\}]*\}.*?from\s+[\'\"`]lucide-react[\'\"`]', content, flags=re.DOTALL):
        # Remove Loader2 from lucide-react import
        def replace_lucide(match):
            imports = match.group(1)
            if 'Loader2' not in imports:
                return match.group(0) # skip if not here
            imports = re.sub(r'\bLoader2\b', '', imports)
            # clean up commas
            imports = re.sub(r',\s*,', ',', imports)
            imports = re.sub(r'\{\s*,', '{ ', imports)
            imports = re.sub(r',\s*\}', ' }', imports)
            if imports.strip() == '{}' or imports.strip() == '{ }':
                return ''
            return 'import {' + imports + '} from "lucide-react";'
            
        new_content = re.sub(r'import\s+\{([^\}]+)\}\s+from\s+[\'\"`]lucide-react[\'\"`];?', replace_lucide, content, flags=re.DOTALL)
        
        # Add the import for CustomLoader
        if 'CustomLoader as Loader2' not in new_content:
            import_str = 'import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";\n'
            new_content = import_str + new_content
            
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f'Updated {filepath}')

for root, dirs, files in os.walk('web/src'):
    for file in files:
        if file.endswith('.tsx'):
            process_file(os.path.join(root, file))
