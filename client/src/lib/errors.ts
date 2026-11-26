import { KdError } from '../api/knowledgeDrop';
import { DbError } from '../api/portalDb';

/** Plain-language message for a failed Ask call. */
export function describeAskError(err: unknown): string {
  if (err instanceof KdError) {
    switch (err.status) {
      case 501:
        return "The Ask feature isn't available on this knowledgeDrop server yet. Your question was saved to your history.";
      case 401:
        return 'knowledgeDrop rejected the company client token. An admin needs to check Setup.';
      case 400:
        return `knowledgeDrop couldn't accept that question: ${err.reason || err.error}`;
      default:
        return `knowledgeDrop returned ${err.status} ${err.error}${err.reason ? `: ${err.reason}` : ''}`;
    }
  }
  return describeError(err);
}

/** Plain-language message for any other error. */
export function describeError(err: unknown): string {
  if (err instanceof KdError) {
    return err.reason ? `${err.error}: ${err.reason}` : `${err.error} (HTTP ${err.status})`;
  }
  if (err instanceof DbError) {
    return err.message;
  }
  if (err instanceof TypeError) {
    return 'Could not reach the server. Check that the knowledgeDrop service and portal are running.';
  }
  return err instanceof Error ? err.message : String(err);
}
