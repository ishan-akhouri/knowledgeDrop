import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as kd from '../api/knowledgeDrop';
import * as portalDb from '../api/portalDb';
import type { NewQuestion, QuestionRecord } from '../api/types';
import { makeAuth, renderWithAuth } from '../test/helpers';
import { AskPage } from './AskPage';

function savedRow(q: NewQuestion): QuestionRecord {
  return { ...q, id: 'q1', analyst_id: 'a1', created_at: '2026-11-25T10:00:00Z' };
}

beforeEach(() => {
  vi.spyOn(portalDb, 'listOwners').mockResolvedValue([
    {
      document_id: 'doc-1',
      filename: '340b-claims-policy.md',
      analyst_id: 'a2',
      uploaded_at: '2026-11-01T00:00:00Z',
      deleted_at: null,
      deleted_by: null,
    },
  ]);
  vi.spyOn(portalDb, 'listDirectory').mockResolvedValue([
    { id: 'a2', display_name: 'Priya Pharmacy', department: 'Pharmacy / 340B' },
  ]);
  vi.spyOn(portalDb, 'insertQuestion').mockImplementation(async (_jwt, q) => savedRow(q));
});

describe('AskPage', () => {
  it('shows the answer with citations and owner, and saves it to history', async () => {
    vi.spyOn(kd, 'ask').mockResolvedValue({
      answer: '340B claims need SCC 20 on pharmacy claims.',
      citations: [{ documentId: 'doc-1', filename: '340b-claims-policy.md', page: null, snippet: 'SCC 20' }],
    });
    const user = userEvent.setup();
    renderWithAuth(<AskPage />);

    await user.type(screen.getByLabelText('Your question'), 'Tell me about 340B claims');
    await user.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByText('340B claims need SCC 20 on pharmacy claims.')).toBeInTheDocument();
    expect(screen.getByText('340b-claims-policy.md')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/uploaded by Priya Pharmacy/)).toBeInTheDocument());
    expect(kd.ask).toHaveBeenCalledWith('kd-token', 'Tell me about 340B claims', 5);
    expect(portalDb.insertQuestion).toHaveBeenCalledWith(
      'test.jwt.token',
      expect.objectContaining({ status: 'answered', question: 'Tell me about 340B claims' }),
    );
    expect(screen.getByRole('button', { name: /Save answer/ })).toBeInTheDocument();
  });

  it('shows a clear message when the service returns 501 and records the attempt', async () => {
    vi.spyOn(kd, 'ask').mockRejectedValue(new kd.KdError(501, 'Not Implemented', 'ask is not implemented'));
    const user = userEvent.setup();
    renderWithAuth(<AskPage />);

    await user.click(screen.getByRole('button', { name: /340B claims\?/ }));
    await user.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByText(/isn't available on this knowledgeDrop server yet/)).toBeInTheDocument();
    expect(portalDb.insertQuestion).toHaveBeenCalledWith(
      'test.jwt.token',
      expect.objectContaining({ status: 'error', answer: null }),
    );
    expect(screen.queryByRole('button', { name: /Save answer/ })).not.toBeInTheDocument();
  });

  it('says when no relevant documentation was found', async () => {
    vi.spyOn(kd, 'ask').mockResolvedValue({ answer: 'I could not find that.', citations: [] });
    const user = userEvent.setup();
    renderWithAuth(<AskPage />);

    await user.type(screen.getByLabelText('Your question'), 'Dental for pets?');
    await user.click(screen.getByRole('button', { name: 'Ask' }));

    expect(await screen.findByText('No relevant internal documentation found.')).toBeInTheDocument();
  });

  it('disables Ask and explains when no knowledgeDrop client is configured', async () => {
    renderWithAuth(<AskPage />, makeAuth({ kdToken: null }));
    await act(async () => {}); // let the owner lookup settle

    expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled();
    expect(screen.getByText(/not connected to knowledgeDrop yet/)).toBeInTheDocument();
    expect(screen.getByText(/Ask a portal administrator/)).toBeInTheDocument();
  });

  it('prefills the question from the ?q= link used by "Ask again"', async () => {
    renderWithAuth(<AskPage />, makeAuth(), '/ask?q=What%20is%20COB%3F');
    await act(async () => {});
    expect(screen.getByLabelText('Your question')).toHaveValue('What is COB?');
  });
});
