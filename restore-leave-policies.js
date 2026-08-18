const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

try {
  execSync('git checkout web/src/pages/LeavePolicies.tsx', { stdio: 'inherit' });
  console.log('Restored LeavePolicies.tsx');
} catch (e) {
  console.error('Git checkout error:', e);
}
