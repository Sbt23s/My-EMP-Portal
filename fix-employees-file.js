const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Fetch the committed version from HEAD~5 or HEAD
try {
  const fileContent = execSync('git show 43fdfa4:web/src/pages/Employees.tsx', { encoding: 'utf8' });
  
  // Add CEO filter to directory query in fileContent
  const updatedContent = fileContent.replace(
    /const res = await api\.get<ApiEnvelope<PageEnvelope<UserSummary>>>\(\s*`\/users\?\$\{params\.toString\(\)\}`\s*\);/g,
    `const res = await api.get<ApiEnvelope<PageEnvelope<UserSummary>>>(
        \`/users?\${params.toString()}\`
      );
      const data = res.data?.data;
      if (data?.content) {
        const content = data.content.filter((e) => {
          const isCeoRecord = e.employeeCode === "PIX-E100" || e.name === "CEO" || e.designationTitle === "CEO";
          const isCurrentUserCeo = user?.employeeCode === "PIX-E100";
          return !isCeoRecord || isCurrentUserCeo;
        });
        return { ...data, content, totalElements: content.length };
      }`
  );

  fs.writeFileSync(path.join(__dirname, 'web/src/pages/Employees.tsx'), updatedContent, 'utf8');
  console.log('Successfully fixed and wrote clean Employees.tsx');
} catch (err) {
  console.error('Error fetching git file:', err);
}
