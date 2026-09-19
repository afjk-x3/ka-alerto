// The KaAlerto mark: a red lamp on a white tower sending signal arcs, over a wave line, on the
// app's blue. Same drawing as android/.../drawable/ic_launcher_foreground.xml (160-unit canvas).
export default function Logo({ size = 48 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 160 160" role="img" aria-label="KaAlerto">
      <rect width="160" height="160" rx="36" fill="#2F7FBF" />
      <path d="M72,80 L88,80 L94,118 L66,118 Z" fill="#fff" />
      <g fill="none" stroke="#fff" strokeWidth="7" strokeLinecap="round">
        <path d="M59,54 q-8,10 0,20" />
        <path d="M101,54 q8,10 0,20" />
        <path d="M48,46 q-14,18 0,36" />
        <path d="M112,46 q14,18 0,36" />
        <path d="M28,132 q13,-9 26,0 t26,0 t26,0 t26,0" />
      </g>
      <circle cx="80" cy="64" r="11" fill="#C42B2B" stroke="#fff" strokeWidth="3" />
    </svg>
  );
}
