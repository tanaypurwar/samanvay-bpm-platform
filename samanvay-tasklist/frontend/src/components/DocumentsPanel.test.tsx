import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { DocumentOptions } from '../api/types';
import { checkFile, DocumentsPanel } from './DocumentsPanel';

const options: DocumentOptions = {
  enabled: true,
  types: [
    { id: 'pan', label: 'PAN card' },
    { id: 'photo', label: 'Photograph' },
  ],
  maxFileSize: 1024,
  maxFiles: 10,
  contentTypes: ['application/pdf', 'image/jpeg', 'image/png'],
};

const pan = {
  key: 'd1',
  fileName: 'pan.pdf',
  typeLabel: 'PAN card',
  contentType: 'application/pdf',
  size: 2048,
  detail: 'Asha Rao · Employee joining form',
};

describe('DocumentsPanel', () => {
  it('lists documents with their type and opens one for preview', async () => {
    const onOpen = vi.fn();
    render(<DocumentsPanel options={options} items={[pan]} onOpen={onOpen} />);

    expect(screen.getByText('PAN card')).toBeInTheDocument();
    expect(screen.getByText('2 KB · Asha Rao · Employee joining form')).toBeInTheDocument();
    await userEvent.click(screen.getByText('pan.pdf'));
    expect(onOpen).toHaveBeenCalledWith('d1');
  });

  it('asks for the document type before a file can be attached', async () => {
    const onAttach = vi.fn();
    render(<DocumentsPanel options={options} items={[]} onOpen={vi.fn()} onAttach={onAttach} />);

    const button = screen.getByRole('button', { name: /attach file/i });
    expect(button).toBeDisabled();

    await userEvent.click(screen.getByLabelText('Document type'));
    await userEvent.click(within(screen.getByRole('listbox')).getByText('Photograph'));
    expect(button).toBeEnabled();

    const file = new File(['png'], 'me.png', { type: 'image/png' });
    fireEvent.change(screen.getByTestId('document-file-input'), { target: { files: [file] } });
    expect(onAttach).toHaveBeenCalledWith(file, 'photo');
  });

  it('refuses files that are not PDFs or images, or too large', () => {
    expect(checkFile(new File(['x'], 'notes.txt', { type: 'text/plain' }), options)).toMatch(/not a PDF/);
    expect(checkFile(new File([new Uint8Array(2048)], 'big.pdf', { type: 'application/pdf' }), options)).toMatch(
      /larger than 1 KB/,
    );
    expect(checkFile(new File(['%PDF'], 'ok.pdf', { type: 'application/pdf' }), options)).toBeNull();
  });

  it('offers removal only for removable documents and hides attaching when not allowed', () => {
    const onRemove = vi.fn();
    render(
      <DocumentsPanel
        options={options}
        items={[pan, { ...pan, key: 'd2', fileName: 'mine.pdf', removable: true }]}
        onOpen={vi.fn()}
        onRemove={onRemove}
        attachHint="Claim this task to attach documents."
      />,
    );
    expect(screen.queryByRole('button', { name: 'Remove pan.pdf' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Remove mine.pdf' }));
    expect(onRemove).toHaveBeenCalledWith('d2');
    expect(screen.queryByRole('button', { name: /attach file/i })).not.toBeInTheDocument();
    expect(screen.getByText('Claim this task to attach documents.')).toBeInTheDocument();
  });
});
