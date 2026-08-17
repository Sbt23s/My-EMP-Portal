import React from 'react';
import { cn } from '@/lib/utils';

export function CustomLoader({ className, ...props }: React.SVGProps<SVGSVGElement>) {
  // Strip out `animate-spin` since we have custom layered spinning logic inside.
  const finalClassName = (className || '').replace(/\banimate-spin\b/g, '').trim();
  
  return (
    <svg 
      viewBox="0 0 100 100" 
      fill="none" 
      xmlns="http://www.w3.org/2000/svg"
      className={cn("overflow-visible drop-shadow-[0_0_8px_rgba(59,130,246,0.6)] text-primary", finalClassName)}
      {...props}
    >
      <defs>
        <filter id="loader-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="2" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
        <style>
          {`
            @keyframes spin-slow { 100% { transform: rotate(360deg); } }
            @keyframes spin-reverse { 100% { transform: rotate(-360deg); } }
            @keyframes pulse-glow { 
              0%, 100% { opacity: 0.8; filter: drop-shadow(0 0 4px currentColor); } 
              50% { opacity: 1; filter: drop-shadow(0 0 12px currentColor); } 
            }
          `}
        </style>
      </defs>

      {/* Outer Dotted Ring */}
      <g style={{ animation: 'spin-slow 12s linear infinite', transformOrigin: '50px 50px' }}>
        {Array.from({ length: 24 }).map((_, i) => (
          <circle 
            key={`outer-${i}`}
            cx="50" cy="8" r="1" 
            fill="currentColor" 
            opacity="0.6"
            transform={`rotate(${(i * 360) / 24} 50 50)`} 
          />
        ))}
      </g>

      {/* Middle Dotted Ring */}
      <g style={{ animation: 'spin-reverse 8s linear infinite', transformOrigin: '50px 50px' }}>
        {Array.from({ length: 16 }).map((_, i) => (
          <circle 
            key={`mid-${i}`}
            cx="50" cy="18" r="1.5" 
            fill="currentColor" 
            opacity="0.8"
            transform={`rotate(${(i * 360) / 16} 50 50)`} 
          />
        ))}
      </g>

      {/* Inner Glowing Ring with heavy dots */}
      <g 
        style={{ animation: 'spin-slow 4s linear infinite', transformOrigin: '50px 50px' }} 
        filter="url(#loader-glow)"
      >
        <circle cx="50" cy="50" r="20" stroke="currentColor" strokeWidth="1.5" opacity="0.4" />
        {Array.from({ length: 10 }).map((_, i) => (
          <circle 
            key={`glow-${i}`}
            cx="50" cy="30" r="2.5" 
            fill="currentColor" 
            transform={`rotate(${(i * 360) / 10} 50 50)`} 
          />
        ))}
      </g>

      {/* Center Hexagon / Flower */}
      <g style={{ animation: 'spin-reverse 6s linear infinite', transformOrigin: '50px 50px' }}>
        {/* Center tiny dot */}
        <circle cx="50" cy="50" r="1.5" fill="currentColor" />
        {/* 6 hollow circles */}
        {Array.from({ length: 6 }).map((_, i) => (
          <circle 
            key={`flower-${i}`}
            cx="50" cy="43.5" r="3.5" 
            stroke="currentColor" 
            strokeWidth="1.5" 
            fill="none"
            transform={`rotate(${(i * 360) / 6} 50 50)`} 
          />
        ))}
      </g>
    </svg>
  );
}
