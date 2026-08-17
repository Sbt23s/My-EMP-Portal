import React from 'react';
import { CustomLoader } from '@/components/ui/custom-loader';

interface PageLoaderProps {
  text?: string;
  className?: string;
}

export function PageLoader({ text = "Loading page details...", className = "" }: PageLoaderProps) {
  return (
    <div className={`flex flex-col h-full w-full items-center justify-center min-h-[50vh] gap-4 p-6 ${className}`}>
      <CustomLoader className="h-16 w-16" />
      {text && (
        <p className="text-sm font-medium text-muted-foreground animate-pulse tracking-wide">
          {text}
        </p>
      )}
    </div>
  );
}
