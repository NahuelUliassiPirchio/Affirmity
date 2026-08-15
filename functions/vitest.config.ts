import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts'],
    // firestore.rules.test.ts requires a running Firestore emulator (see `npm run test:rules`,
    // which wraps it in `firebase emulators:exec`) -- excluded from the default `npm test` run so
    // the fast unit-test loop never depends on the emulator being available.
    exclude: ['test/firestore.rules.test.ts'],
  },
});
