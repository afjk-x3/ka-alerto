import path from 'path';
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'standalone',
  // Without this Next walks up and picks a stray lockfile in the home directory as the root.
  turbopack: { root: path.resolve(__dirname) },
};

export default nextConfig;
