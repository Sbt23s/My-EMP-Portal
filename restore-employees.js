const { execSync } = require('child_process');
try {
  execSync('git checkout web/src/pages/Employees.tsx', { stdio: 'inherit' });
  console.log('Successfully restored Employees.tsx from git');
} catch (e) {
  console.error('Error restoring file:', e.message);
}
