import {
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreatePartner, usePartners } from './api';

export function PartnersPanel() {
  const { canDo } = useAuth();
  const { data, isLoading } = usePartners();
  const create = useCreatePartner();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: {
      code: '',
      name: '',
      vendor: true,
      customer: false,
      taxId: '',
      paymentTermsDays: 30,
      apAccountCode: '',
      arAccountCode: '',
    },
    validate: {
      code: (v) => (v.trim() ? null : 'Required'),
      name: (v) => (v.trim() ? null : 'Required'),
      vendor: (v, values) => (v || values.customer ? null : 'Pick vendor and/or customer'),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({
        code: v.code,
        name: v.name,
        vendor: v.vendor,
        customer: v.customer,
        taxId: v.taxId.trim() || undefined,
        paymentTermsDays: Number(v.paymentTermsDays) || 30,
        apAccountCode: v.apAccountCode.trim() || undefined,
        arAccountCode: v.arAccountCode.trim() || undefined,
      });
      notifySuccess(`Partner ${v.code} created`);
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  return (
    <Stack>
      {canDo('masterdata.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            New partner
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={680}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Code</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Roles</Table.Th>
              <Table.Th>Tax ID</Table.Th>
              <Table.Th ta="right">Terms (days)</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((p) => (
              <Table.Tr key={p.id}>
                <Table.Td>{p.code}</Table.Td>
                <Table.Td>{p.name}</Table.Td>
                <Table.Td>
                  <Group gap={4}>
                    {p.vendor && <Badge variant="light">Vendor</Badge>}
                    {p.customer && <Badge variant="light" color="grape">Customer</Badge>}
                  </Group>
                </Table.Td>
                <Table.Td>{p.taxId ?? '—'}</Table.Td>
                <Table.Td ta="right">{p.paymentTermsDays}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No partners yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New partner">
        <form onSubmit={submit}>
          <Stack>
            <TextInput label="Code" required {...form.getInputProps('code')} />
            <TextInput label="Name" required {...form.getInputProps('name')} />
            <Group grow>
              <Switch label="Vendor" {...form.getInputProps('vendor', { type: 'checkbox' })} />
              <Switch label="Customer" {...form.getInputProps('customer', { type: 'checkbox' })} />
            </Group>
            {form.errors.vendor && (
              <Text size="xs" c="red">
                {form.errors.vendor}
              </Text>
            )}
            <TextInput label="Tax ID" {...form.getInputProps('taxId')} />
            <NumberInput label="Payment terms (days)" min={0} {...form.getInputProps('paymentTermsDays')} />
            <Group grow>
              <TextInput label="AP account (override)" {...form.getInputProps('apAccountCode')} />
              <TextInput label="AR account (override)" {...form.getInputProps('arAccountCode')} />
            </Group>
            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" loading={create.isPending}>
                Create
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}
