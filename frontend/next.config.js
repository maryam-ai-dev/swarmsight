/** @type {import('next').NextConfig} */

// The browser talks only to this Next.js origin. These rewrites proxy API calls
// to the backend services from the server side (inside the container), so the
// page works with just port 3000 forwarded and never makes a cross-origin call.
// Override the targets with AUTHORITY_ORIGIN / INTELLIGENCE_ORIGIN if the
// services live elsewhere.
const AUTHORITY_ORIGIN = process.env.AUTHORITY_ORIGIN || "http://localhost:8080";
const INTELLIGENCE_ORIGIN =
  process.env.INTELLIGENCE_ORIGIN || "http://localhost:8000";

const nextConfig = {
  reactStrictMode: true,
  async rewrites() {
    return [
      { source: "/_authority/:path*", destination: `${AUTHORITY_ORIGIN}/:path*` },
      {
        source: "/_intelligence/:path*",
        destination: `${INTELLIGENCE_ORIGIN}/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
