import type { RJSFSchema } from '@rjsf/utils';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { FormData } from '../api/types';
import { DynamicForm, toErrorSchema } from './DynamicForm';

const schema: RJSFSchema = {
  type: 'object',
  required: ['decision'],
  properties: {
    invoiceNumber: { type: 'string', title: 'Invoice number', readOnly: true },
    decision: { type: 'string', title: 'Decision', enum: ['approve', 'reject'] },
    comment: { type: 'string', title: 'Comment' },
  },
};

function Harness({ onSubmit, initial = {} }: { onSubmit: (data: FormData) => void; initial?: FormData }) {
  const [data, setData] = useState<FormData>(initial);
  return <DynamicForm schema={schema} formData={data} onChange={setData} onSubmit={onSubmit} submitLabel="Complete task" />;
}

describe('DynamicForm', () => {
  it('renders fields and initial values from the schema', () => {
    render(<Harness onSubmit={vi.fn()} initial={{ invoiceNumber: 'INV-7' }} />);

    expect(screen.getByLabelText(/Invoice number/)).toHaveValue('INV-7');
    expect(screen.getByLabelText(/Comment/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Complete task' })).toBeEnabled();
  });

  it('blocks submission while required fields are missing', async () => {
    const onSubmit = vi.fn();
    render(<Harness onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole('button', { name: 'Complete task' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText(/must have required property/i)).toBeInTheDocument();
  });

  it('submits entered data and keeps it after submission', async () => {
    const onSubmit = vi.fn();
    render(<Harness onSubmit={onSubmit} initial={{ decision: 'approve' }} />);

    await userEvent.type(screen.getByLabelText(/Comment/), 'Looks good');
    await userEvent.click(screen.getByRole('button', { name: 'Complete task' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ decision: 'approve', comment: 'Looks good' }));
    // the parent owns the data, so a failed server call leaves the input in place
    expect(screen.getByLabelText(/Comment/)).toHaveValue('Looks good');
  });

  it('shows server-side field errors next to the field', () => {
    render(
      <DynamicForm
        schema={schema}
        formData={{ decision: 'reject' }}
        onChange={vi.fn()}
        serverErrors={[{ field: 'comment', message: 'must not be shorter than 10 characters' }]}
      />,
    );

    expect(screen.getByText('must not be shorter than 10 characters')).toBeInTheDocument();
  });
});

describe('toErrorSchema', () => {
  it('nests dotted field paths', () => {
    expect(toErrorSchema([{ field: 'address.city', message: 'required' }])).toEqual({
      address: { city: { __errors: ['required'] } },
    });
    expect(toErrorSchema([])).toBeUndefined();
  });
});
