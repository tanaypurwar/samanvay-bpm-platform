-- Development only: the example "invoice-approval" form from examples/invoice-approval.form.json,
-- published as version 1. Idempotent; safe to re-run.

insert into form_definition (form_key, name, description, status, draft_schema_json, draft_ui_schema_json, created_at, updated_at)
select 'invoice-approval', 'Approve invoice', 'Example form for examples/invoice-approval.bpmn', 'PUBLISHED',
  '{"title":"Approve invoice","type":"object","required":["decision"],"properties":{"invoiceNumber":{"type":"string","title":"Invoice number","readOnly":true},"vendor":{"type":"string","title":"Vendor","readOnly":true},"amount":{"type":"number","title":"Amount (INR)","readOnly":true},"invoiceDate":{"type":"string","format":"date","title":"Invoice date","readOnly":true},"decision":{"type":"string","title":"Decision","oneOf":[{"const":"approve","title":"Approve"},{"const":"reject","title":"Reject"}]},"costCenter":{"type":"string","title":"Cost center","oneOf":[{"const":"OPS","title":"Operations"},{"const":"IT","title":"Information Technology"},{"const":"HR","title":"Human Resources"},{"const":"FIN","title":"Finance"}]},"paymentDueDate":{"type":"string","format":"date","title":"Payment due date"},"comment":{"type":"string","title":"Comment","maxLength":1000}},"allOf":[{"if":{"properties":{"decision":{"const":"approve"}},"required":["decision"]},"then":{"required":["costCenter","paymentDueDate"]}},{"if":{"properties":{"decision":{"const":"reject"}},"required":["decision"]},"then":{"required":["comment"],"properties":{"comment":{"minLength":10}}}}]}',
  '{"ui:order":["invoiceNumber","vendor","amount","invoiceDate","decision","costCenter","paymentDueDate","comment"],"decision":{"ui:widget":"radio"},"comment":{"ui:widget":"textarea","ui:options":{"rows":4},"ui:help":"Required when rejecting (at least 10 characters)."}}',
  current_timestamp, current_timestamp
where not exists (select 1 from form_definition where form_key = 'invoice-approval');

insert into form_version (form_definition_id, version, schema_json, ui_schema_json, published_at, published_by)
select d.id, 1, d.draft_schema_json, d.draft_ui_schema_json, current_timestamp, 'seed'
from form_definition d
where d.form_key = 'invoice-approval'
  and not exists (select 1 from form_version v where v.form_definition_id = d.id);
