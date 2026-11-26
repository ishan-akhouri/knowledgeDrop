import { useCallback, useState } from 'react';
import * as kd from '../api/knowledgeDrop';
import { useAuth } from '../auth/AuthContext';
import { describeError } from './errors';

/** Downloads a knowledgeDrop document by name, sending the client token. */
export function useDownload() {
  const { kdToken } = useAuth();
  const [error, setError] = useState<string | null>(null);

  const download = useCallback(
    async (filename: string) => {
      setError(null);
      if (!kdToken) {
        setError('The portal is not connected to knowledgeDrop yet.');
        return;
      }
      try {
        const blob = await kd.readDocument(kdToken, filename);
        kd.saveBlob(blob, filename);
      } catch (err) {
        setError(`Could not download ${filename}: ${describeError(err)}`);
      }
    },
    [kdToken],
  );

  return { download, error, clearError: () => setError(null) };
}
