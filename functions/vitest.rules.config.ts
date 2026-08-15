import { defineConfig } from 'vitest/config';

// Separate config used only by `npm run test:rules` (invoked inside `firebase emulators:exec`).
export default defineConfig({
  test: {
    environment: 'node',
    include: ['test/firestore.rules.test.ts'],
  },
});
