#!/usr/bin/env node
import { OpenLibraryServer } from '../node_modules/mcp-open-library/build/index.js';

const server = new OpenLibraryServer();
server.run().catch(e => {
  console.error('[mcp-open-library] Fatal:', e);
  process.exit(1);
});
