/**
 * Cloud Tasks enqueue: deterministic task naming so Cloud Tasks' own `ALREADY_EXISTS` rejection
 * gives idempotency for free (design.md's "Planner idempotency" decision).
 */

export interface PlannedTaskRef {
  uid: string;
  localDay: number;
  channel: string;
  slot: number;
  atMillis: number;
}

/** Deterministic Cloud Task name: `{uid}-{localDay}-{channel}-{slot}`. */
export function taskName(uid: string, localDay: number, channel: string, slot: number): string {
  return `${uid}-${localDay}-${channel}-${slot}`;
}

export interface EnqueueResult {
  created: boolean;
  name: string;
}

/**
 * Port-agnostic Cloud Tasks client. The real implementation (see index.ts) wraps
 * `@google-cloud/tasks`'s `CloudTasksClient.createTask` and treats gRPC code 6 (`ALREADY_EXISTS`)
 * as `{ created: false }` rather than an error.
 */
export interface CloudTasksClient {
  createTask(params: {
    queuePath: string;
    taskName: string;
    url: string;
    scheduleTimeSeconds: number;
    payload: unknown;
  }): Promise<{ created: boolean }>;
}

export async function enqueueTask(
  client: CloudTasksClient,
  queuePath: string,
  url: string,
  task: PlannedTaskRef,
): Promise<EnqueueResult> {
  const name = taskName(task.uid, task.localDay, task.channel, task.slot);
  const scheduleTimeSeconds = Math.floor(task.atMillis / 1000);
  const result = await client.createTask({ queuePath, taskName: name, url, scheduleTimeSeconds, payload: task });
  return { created: result.created, name };
}
