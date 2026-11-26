import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import * as kd from '../api/knowledgeDrop';
import * as portalDb from '../api/portalDb';
import { renderWithAuth } from '../test/helpers';
import { UploadPage } from './UploadPage';

describe('UploadPage', () => {
  it('uploads, records the owner, and polls until the document is ready', async () => {
    vi.spyOn(kd, 'uploadDocument').mockResolvedValue({ documentId: 'doc-9', status: 'processing' });
    const list = vi
      .spyOn(kd, 'listDocuments')
      .mockResolvedValue([
        { documentId: 'doc-9', filename: 'cob.md', type: 'text/markdown', uploadTime: '', status: 'ready' },
      ]);
    vi.spyOn(portalDb, 'markOwnerDeleted').mockResolvedValue();
    vi.spyOn(portalDb, 'recordOwner').mockResolvedValue();
    vi.spyOn(window, 'setInterval').mockImplementation(((fn: () => void) => {
      queueMicrotask(fn); // run the first poll right away instead of after 3s
      return 1;
    }) as unknown as typeof window.setInterval);

    renderWithAuth(<UploadPage />);
    const file = new File(['# COB'], 'cob.md', { type: 'text/markdown' });
    fireEvent.change(screen.getByTestId('file-input'), { target: { files: [file] } });

    await waitFor(() => expect(screen.getByText('ready')).toBeInTheDocument());
    expect(kd.uploadDocument).toHaveBeenCalledWith('kd-token', file);
    expect(portalDb.markOwnerDeleted).toHaveBeenCalledWith('test.jwt.token', 'cob.md', expect.any(String));
    expect(portalDb.recordOwner).toHaveBeenCalledWith('test.jwt.token', 'doc-9', 'cob.md');
    expect(list).toHaveBeenCalled();
  });

  it('shows a friendly message when the file type is rejected', async () => {
    vi.spyOn(kd, 'uploadDocument').mockRejectedValue(new kd.KdError(415, 'Unsupported Media Type', ''));

    renderWithAuth(<UploadPage />);
    fireEvent.change(screen.getByTestId('file-input'), {
      target: { files: [new File(['x'], 'scan.heic', { type: 'image/heic' })] },
    });

    expect(await screen.findByText('This file type is not supported.')).toBeInTheDocument();
  });
});
