import React from 'react';
import { cn } from '@/lib/utils';

export function CustomLoader({ className, ...props }: React.SVGProps<SVGSVGElement>) {
  // Strip out `animate-spin` since we have custom layered spinning logic inside.
  // We keep all other classes (like text-primary, h-4, w-4, etc.)
  const finalClassName = (className || '').replace(/\banimate-spin\b/g, '').trim();
  
  return (
    <svg 
      viewBox="0 0 100 100" 
      fill="none" 
      xmlns="http://www.w3.org/2000/svg"
      className={cn("overflow-visible", finalClassName)}
      {...props}
    >
      <defs>
        <filter id="glow-effect" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="2.5" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>

      {/* Outer Dotted Ring */}
      <g style={{ animation: 'spin 12s linear infinite', transformOrigin: '50% 50%' }}>
        {Array.from({ length: 24 }).map((_, i) => {
          const angle = (i * 360) / 24;
          return (
            <circle 
              key={`outer-${i}`}
              cx="50" cy="4" r="1.5" 
              fill="currentColor" 
              transform={`rotate(${angle} 50 50)`} 
            />
          );
        })}
      </g>

      {/* Middle Dotted Ring */}
      <g style={{ animation: 'spin 8s linear infinite reverse', transformOrigin: '50% 50%' }}>
        {Array.from({ length: 16 }).map((_, i) => {
          const angle = (i * 360) / 16;
          return (
            <circle 
              key={`mid-${i}`}
              cx="50" cy="15" r="1.5" 
              fill="currentColor" 
              transform={`rotate(${angle} 50 50)`} 
            />
          );
        })}
      </g>

      {/* Inner Glowing Ring with heavy dots */}
      <g style={{ animation: 'spin 4s linear infinite', transformOrigin: '50% 50%' }} filter="url(#glow-effect)">
        <circle cx="50" cy="50" r="18" stroke="currentColor" strokeWidth="2" opacity="0.8" />
        {Array.from({ length: 10 }).map((_, i) => {
          const angle = (i * 360) / 10;
          return (
            <circle 
              key={`glow-${i}`}
              cx="50" cy="32" r="3" 
              fill="currentColor" 
              transform={`rotate(${angle} 50 50)`} 
            />
          );
        })}
      </g>

      {/* Center Hexagon / Flower */}
      <g style={{ animation: 'spin 5s linear infinite reverse', transformOrigin: '50% 50%' }}>
        {/* Center tiny dot */}
        <circle cx="50" cy="50" r="1.5" fill="currentColor" />
        {/* 6 hollow circles */}
        {Array.from({ length: 6 }).map((_, i) => {
          const angle = (i * 360) / 6;
          return (
            <circle 
              key={`flower-${i}`}
              cx="50" cy="43.5" r="3.5" 
              stroke="currentColor" 
              strokeWidth="1.5" 
              fill="none"
              transform={`rotate(${angle} 50 50)`} 
            />
          );
        })}
      </g>
    </svg>
  );
}
