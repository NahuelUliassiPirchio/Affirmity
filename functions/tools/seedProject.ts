/**
 * Resolves the Firebase project an admin tool should write to. Never guesses: a missing project
 * throws instead of letting the Admin SDK pick the ADC quota project, which once seeded the wrong
 * Firebase project.
 */
export function resolveSeedProject(argv: string[], env: Record<string, string | undefined>): string {
  const flagIndex = argv.indexOf('--project');
  if (flagIndex !== -1) {
    const value = argv[flagIndex + 1];
    if (!value || value.startsWith('--')) throw new Error('--project requires a project id');
    return value;
  }
  const fromEnv = env.GOOGLE_CLOUD_PROJECT;
  if (fromEnv) return fromEnv;
  throw new Error('Target project required: pass --project <id> or set GOOGLE_CLOUD_PROJECT');
}
