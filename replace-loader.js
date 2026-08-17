const fs = require('fs');
const path = require('path');

function walkDir(dir, callback) {
    fs.readdirSync(dir).forEach(f => {
        let dirPath = path.join(dir, f);
        let isDirectory = fs.statSync(dirPath).isDirectory();
        if (isDirectory) {
            walkDir(dirPath, callback);
        } else if (dirPath.endsWith('.tsx')) {
            callback(dirPath);
        }
    });
}

function processFile(filepath) {
    let content = fs.readFileSync(filepath, 'utf8');
    
    // Check if Loader2 is imported from lucide-react
    if (!content.includes('Loader2') || !content.includes('lucide-react')) return;

    let match = content.match(/import\s+\{([^\}]+)\}\s+from\s+['"`]lucide-react['"`]/);
    if (!match || !match[1].includes('Loader2')) return;

    let imports = match[1];
    imports = imports.replace(/\bLoader2\b/g, '');
    imports = imports.replace(/,\s*,/g, ',');
    imports = imports.replace(/\{\s*,/g, '{ ');
    imports = imports.replace(/,\s*\}/g, ' }');

    let replacement = '';
    if (imports.trim() !== '{}' && imports.trim() !== '{ }' && imports.trim() !== '') {
        replacement = `import {${imports}} from "lucide-react";`;
    }

    content = content.replace(/import\s+\{[^\}]+\}\s+from\s+['"`]lucide-react['"`];?/, replacement);
    
    if (!content.includes('CustomLoader as Loader2')) {
        content = 'import { CustomLoader as Loader2 } from "@/components/ui/custom-loader";\n' + content;
    }
    
    fs.writeFileSync(filepath, content, 'utf8');
    console.log(`Updated ${filepath}`);
}

walkDir(path.join(__dirname, 'web', 'src'), processFile);
console.log('Replacement complete!');
