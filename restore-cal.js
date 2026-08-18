const { execSync } = require('child_process');
try {
  execSync('git checkout web/src/pages/Calendar.tsx', { stdio: 'inherit' });
  console.log('Restored Calendar.tsx');
} catch (e) {}
