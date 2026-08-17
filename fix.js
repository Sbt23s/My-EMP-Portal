const { execSync } = require('child_process');
try {
  console.log('Restoring TeamAttendance.tsx to its undamaged state...');
  execSync('git checkout web/src/pages/TeamAttendance.tsx', { stdio: 'inherit' });
  console.log('Successfully restored! No damage done.');
} catch (error) {
  console.error('Failed to restore:', error.message);
}
