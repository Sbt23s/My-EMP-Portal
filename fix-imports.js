const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');



function fixCommas(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      fixCommas(fullPath);
    } else if (fullPath.endsWith('.tsx') || fullPath.endsWith('.ts')) {
      let content = fs.readFileSync(fullPath, 'utf8');
      
      let newContent = content
        .replace(/\{\s*,/g, '{')
        .replace(/,\s*,/g, ',')
        .replace(/,\s*\}/g, '}');
        
      if (content !== newContent) {
        fs.writeFileSync(fullPath, newContent, 'utf8');
        console.log(`Fixed stray commas in: ${fullPath}`);
      }
    }
  }
}

console.log('Scanning for and fixing syntax errors...');
fixCommas(path.join(__dirname, 'web/src'));
console.log('Done! All files fixed successfully.');
