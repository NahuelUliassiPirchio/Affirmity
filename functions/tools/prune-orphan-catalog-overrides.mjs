#!/usr/bin/env node
// Deletes users/{uid}/catalogOverrides docs whose id is no longer a valid catalog affirmation id
// after the v5 catalog trim (2712 -> 888 ids). Dry-run by default; pass --apply to actually delete.
//
// Usage (from functions/):
//   gcloud auth application-default login          # once, with an account that can write the project
//   node tools/prune-orphan-catalog-overrides.mjs --email you@example.com
//   node tools/prune-orphan-catalog-overrides.mjs --uid <firebase-auth-uid> --apply

import { readFileSync } from "node:fs";
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const PROJECT_ID = "affirmity-7ace6";
const CATALOG_ASSET_PATH = "../../app/src/main/assets/catalog.v1.json";

function parseArgs(argv) {
  const args = { apply: false };
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (key === "--apply") {
      args.apply = true;
      continue;
    }
    if (!key?.startsWith("--") || argv[i + 1] === undefined) {
      throw new Error(`Bad arguments near "${key ?? ""}". Expected --uid, --email, or --apply.`);
    }
    args[key.slice(2)] = argv[i + 1];
    i += 1;
  }
  return args;
}

const args = parseArgs(process.argv.slice(2));
if (!args.uid && !args.email) {
  console.error("Provide --uid <uid> or --email <email>.");
  process.exit(1);
}

const catalog = JSON.parse(readFileSync(new URL(CATALOG_ASSET_PATH, import.meta.url), "utf8"));
const validIds = new Set(catalog.affirmations.map((a) => a.id));
console.log(`[prune-orphan-catalog-overrides] catalog.v1.json version=${catalog.version}, ${validIds.size} valid ids`);

initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID });

const uid = args.uid ?? (await getAuth().getUserByEmail(args.email)).uid;
const collection = getFirestore().collection(`users/${uid}/catalogOverrides`);
const snapshot = await collection.get();

const orphans = snapshot.docs.filter((doc) => !validIds.has(doc.id));
console.log(`[prune-orphan-catalog-overrides] ${snapshot.size} override docs total, ${orphans.length} orphaned`);
for (const doc of orphans) {
  console.log(`  - ${doc.id}`);
}

if (!args.apply) {
  console.log("[prune-orphan-catalog-overrides] dry run only -- rerun with --apply to delete these docs");
  process.exit(0);
}

let batch = getFirestore().batch();
let count = 0;
for (const doc of orphans) {
  batch.delete(doc.ref);
  count += 1;
  if (count % 400 === 0) {
    await batch.commit();
    batch = getFirestore().batch();
  }
}
if (count % 400 !== 0) {
  await batch.commit();
}
console.log(`[prune-orphan-catalog-overrides] deleted ${orphans.length} orphaned override docs`);
