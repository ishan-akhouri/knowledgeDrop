import { useCallback, useEffect, useState } from 'react';
import * as portalDb from '../api/portalDb';
import type { DirectoryEntry, DocumentOwner } from '../api/types';
import { useSession } from '../auth/AuthContext';

export interface OwnerLookup {
  /** Display name of the analyst who uploaded a document, or "Unknown". */
  ownerOf: (documentId: string) => string;
  /** Display name for any analyst id. */
  nameOf: (analystId: string | null | undefined) => string;
  directory: DirectoryEntry[];
  owners: DocumentOwner[];
  reload: () => Promise<void>;
}

export function useOwnerNames(): OwnerLookup {
  const { jwt } = useSession();
  const [owners, setOwners] = useState<DocumentOwner[]>([]);
  const [directory, setDirectory] = useState<DirectoryEntry[]>([]);

  const reload = useCallback(async () => {
    try {
      const [o, d] = await Promise.all([portalDb.listOwners(jwt), portalDb.listDirectory(jwt)]);
      setOwners(o);
      setDirectory(d);
    } catch {
      // Owner names are decoration; the page still works without them.
    }
  }, [jwt]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const names = new Map(directory.map((a) => [a.id, a.display_name]));
  const ownerByDoc = new Map(owners.map((o) => [o.document_id, o.analyst_id]));

  const nameOf = (id: string | null | undefined) => (id && names.get(id)) || 'Unknown';
  const ownerOf = (documentId: string) => nameOf(ownerByDoc.get(documentId));

  return { ownerOf, nameOf, directory, owners, reload };
}
