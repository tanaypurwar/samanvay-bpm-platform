import { fireEvent, render, screen } from '@testing-library/react-native';
import { useState } from 'react';
import { PaperProvider } from 'react-native-paper';
import example from '../../../examples/invoice-approval.form.json';
import type { FormData, JsonSchema, UiSchema } from '../api/types';
import { FormRenderer } from './FormRenderer';

const schema = example.schema as unknown as JsonSchema;
const uiSchema = example.uiSchema as UiSchema;

function Harness({ initial, onData }: { initial: FormData; onData: (data: FormData) => void }) {
  const [data, setData] = useState<FormData>(initial);
  return (
    <PaperProvider>
      <FormRenderer
        schema={schema}
        uiSchema={uiSchema}
        formData={data}
        onChange={(next) => {
          setData(next);
          onData(next);
        }}
        errors={{ comment: 'Enter at least 10 characters.' }}
      />
    </PaperProvider>
  );
}

// Paper animates labels and helper text; fake timers keep those animations inside the test
jest.useFakeTimers();

describe('FormRenderer', () => {
  it('shows read-only values as a summary and editable fields as inputs', () => {
    render(<Harness initial={{ invoiceNumber: 'INV-7', vendor: 'Acme', amount: 1250 }} onData={jest.fn()} />);

    expect(screen.getByText('INV-7')).toBeTruthy();
    expect(screen.getByText('Acme')).toBeTruthy();
    expect(screen.getByText('Approve')).toBeTruthy();
    expect(screen.getByText('Decision *')).toBeTruthy();
    expect(screen.getByText('Enter at least 10 characters.')).toBeTruthy();
  });

  it('marks conditional fields required once a choice is made, and reports typed values', () => {
    const onData = jest.fn();
    render(<Harness initial={{}} onData={onData} />);

    fireEvent.press(screen.getByText('Approve'));

    expect(onData).toHaveBeenLastCalledWith({ decision: 'approve' });
    expect(screen.getAllByText('Cost center *').length).toBeGreaterThan(0);
  });
});
