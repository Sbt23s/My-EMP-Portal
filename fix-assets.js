const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const target = path.join(__dirname, 'web', 'src', 'pages', 'Assets.tsx');
try {
  execSync(`git checkout "${target}"`);
  console.log("Successfully restored Assets.tsx from git HEAD");
  
  let content = fs.readFileSync(target, 'utf8');
  content = content.replace(
    'import { EmptyState } from "@/components/EmptyState";',
    'import { EmptyState } from "@/components/EmptyState";\nimport { PageLoader } from "@/components/ui/page-loader";'
  );
  content = content.replace(
    '<Skeleton className="h-40" />',
    '<PageLoader text="Loading your assigned assets..." className="min-h-[200px]" />'
  );
  fs.writeFileSync(target, content, 'utf8');
  console.log("Cleanly updated Assets.tsx with PageLoader");
} catch (e) {
  console.error("Error restoring Assets.tsx:", e.message);
}
