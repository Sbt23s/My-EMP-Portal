const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const target = path.join(__dirname, 'web', 'src', 'pages', 'Assets.tsx');
try {
  execSync(`git checkout "${target}"`);
  let content = fs.readFileSync(target, 'utf8');
  if (!content.includes('PageLoader')) {
    content = content.replace(
      'import { EmptyState } from "@/components/EmptyState";',
      'import { EmptyState } from "@/components/EmptyState";\nimport { PageLoader } from "@/components/ui/page-loader";'
    );
    content = content.replace(
      '<Skeleton className="h-40" />',
      '<PageLoader text="Loading assigned assets..." className="min-h-[200px]" />'
    );
    fs.writeFileSync(target, content, 'utf8');
  }
  console.log("Assets.tsx cleanly verified");
} catch (e) {
  console.error("Fix assets error:", e.message);
}
