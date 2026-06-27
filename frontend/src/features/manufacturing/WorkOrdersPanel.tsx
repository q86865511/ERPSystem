import { useState } from 'react';
import {
  Badge,
  Button,
  Drawer,
  Group,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreateWorkOrderRequest } from '../../api/types';
import { ItemSelect, LocationSelect } from '../../components/EntitySelect';
import { MoneyText } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { useItemMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import {
  useBoms,
  useCancelWorkOrder,
  useCompleteWorkOrder,
  useCreateWorkOrder,
  useIssueWorkOrder,
  useReleaseWorkOrder,
  useWorkOrder,
  useWorkOrders,
} from './api';

type ActionKind = 'issue' | 'complete' | 'cancel';

export function WorkOrdersPanel() {
  const { canDo } = useAuth();
  const writable = canDo('manufacturing.write');
  const list = useWorkOrders();
  const boms = useBoms();
  const items = useItemMap();
  const create = useCreateWorkOrder();
  const release = useReleaseWorkOrder();
  const issue = useIssueWorkOrder();
  const complete = useCompleteWorkOrder();
  const cancel = useCancelWorkOrder();

  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useWorkOrder(detailId);

  // create form
  const [itemId, setItemId] = useState<number | null>(null);
  const [bomId, setBomId] = useState<number | null>(null);
  const [qtyToProduce, setQtyToProduce] = useState('1');

  // action modal
  const [action, setAction] = useState<ActionKind | null>(null);
  const [locationId, setLocationId] = useState<number | null>(null);
  const [postingDate, setPostingDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const [qtyProduced, setQtyProduced] = useState('');

  const bomOptions = (boms.data ?? [])
    .filter((b) => itemId == null || b.parentItemId === itemId)
    .map((b) => ({ value: String(b.id), label: `v${b.version} (output ${b.outputQty})` }));

  const openCreate = () => {
    setItemId(null);
    setBomId(null);
    setQtyToProduce('1');
    open();
  };

  const submitCreate = async () => {
    if (!itemId || !bomId || !qtyToProduce) {
      notifyError('Pick an item, a BOM and a quantity');
      return;
    }
    const body: CreateWorkOrderRequest = { itemId, bomId, qtyToProduce };
    try {
      const wo = await create.mutateAsync(body);
      notifySuccess(`Work order ${wo?.woNumber} created`);
      close();
      if (wo?.id != null) setDetailId(wo.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const doRelease = async (id: number) => {
    try {
      await release.mutateAsync(id);
      notifySuccess('Work order released (BOM snapshotted)');
    } catch (e) {
      notifyError(e);
    }
  };

  const openAction = (kind: ActionKind) => {
    setLocationId(null);
    setQtyProduced(detail.data?.qtyToProduce ?? '');
    setAction(kind);
  };

  const submitAction = async () => {
    if (detailId == null || !locationId) {
      notifyError('Pick a stock location');
      return;
    }
    try {
      if (action === 'issue') {
        await issue.mutateAsync({ id: detailId, stockLocationId: locationId, postingDate: postingDate ?? undefined });
        notifySuccess('Components issued to WIP');
      } else if (action === 'complete') {
        if (!qtyProduced) {
          notifyError('Enter quantity produced');
          return;
        }
        await complete.mutateAsync({
          id: detailId,
          qtyProduced,
          stockLocationId: locationId,
          postingDate: postingDate ?? undefined,
        });
        notifySuccess('Work order completed; finished goods received');
      } else if (action === 'cancel') {
        await cancel.mutateAsync({ id: detailId, stockLocationId: locationId, postingDate: postingDate ?? undefined });
        notifySuccess('Work order cancelled; issued components returned');
      }
      setAction(null);
    } catch (e) {
      notifyError(e);
    }
  };

  const status = detail.data?.status;
  const rows = list.data ?? [];
  return (
    <Stack>
      {writable && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openCreate}>
            New work order
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={640}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>WO #</Table.Th>
              <Table.Th>Item</Table.Th>
              <Table.Th ta="right">To produce</Table.Th>
              <Table.Th ta="right">Produced</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((w) => (
              <Table.Tr key={w.id}>
                <Table.Td>{w.woNumber}</Table.Td>
                <Table.Td>{w.itemId != null ? (items.get(w.itemId) ?? w.itemId) : '—'}</Table.Td>
                <Table.Td ta="right">{w.qtyToProduce}</Table.Td>
                <Table.Td ta="right">{w.qtyProduced}</Table.Td>
                <Table.Td>
                  <StatusBadge status={w.status} />
                </Table.Td>
                <Table.Td ta="right">
                  <Button size="xs" variant="subtle" onClick={() => setDetailId(w.id ?? null)}>
                    View
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {!list.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No work orders yet.
        </Text>
      )}

      {/* Create */}
      <Modal opened={opened} onClose={close} title="New work order" size="md">
        <Stack>
          <ItemSelect
            label="Finished item"
            itemType="FINISHED"
            value={itemId}
            onChange={(id) => {
              setItemId(id);
              setBomId(null);
            }}
          />
          <Select
            label="BOM"
            placeholder="BOM for this item"
            data={bomOptions}
            value={bomId != null ? String(bomId) : null}
            onChange={(v) => setBomId(v ? Number(v) : null)}
          />
          <TextInput label="Quantity to produce" value={qtyToProduce} onChange={(e) => setQtyToProduce(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Cancel
            </Button>
            <Button onClick={submitCreate} loading={create.isPending}>
              Create
            </Button>
          </Group>
        </Stack>
      </Modal>

      {/* Detail with state-machine actions */}
      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={`Work order ${detail.data?.woNumber ?? ''}`}
      >
        {detail.data && (
          <Stack>
            <Group justify="space-between">
              <Group>
                <StatusBadge status={status} />
                <Text size="sm" c="dimmed">
                  {detail.data.itemId != null ? items.get(detail.data.itemId) : ''} · produce{' '}
                  {detail.data.qtyToProduce}
                </Text>
              </Group>
              <Text size="sm">
                Component cost <MoneyText value={detail.data.totalComponentCost} />
              </Text>
            </Group>

            {writable && (
              <Group>
                <Button size="xs" disabled={status !== 'DRAFT'} loading={release.isPending} onClick={() => detailId && doRelease(detailId)}>
                  Release
                </Button>
                <Button size="xs" disabled={status !== 'RELEASED'} onClick={() => openAction('issue')}>
                  Issue
                </Button>
                <Button size="xs" disabled={status !== 'IN_PROGRESS'} onClick={() => openAction('complete')}>
                  Complete
                </Button>
                <Button
                  size="xs"
                  color="red"
                  variant="light"
                  disabled={status !== 'RELEASED' && status !== 'IN_PROGRESS'}
                  onClick={() => openAction('cancel')}
                >
                  Cancel
                </Button>
              </Group>
            )}

            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Component</Table.Th>
                  <Table.Th ta="right">Planned</Table.Th>
                  <Table.Th ta="right">Consumed</Table.Th>
                  <Table.Th ta="right">Value</Table.Th>
                  <Table.Th>Posting</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.data.components ?? []).map((c) => (
                  <Table.Tr key={c.id}>
                    <Table.Td>
                      {c.componentItemId != null ? (items.get(c.componentItemId) ?? c.componentItemId) : '—'}
                    </Table.Td>
                    <Table.Td ta="right">{c.plannedQty}</Table.Td>
                    <Table.Td ta="right">{c.consumedQty}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={c.consumedValue} />
                    </Table.Td>
                    <Table.Td>
                      {c.journalEntryId != null && (
                        <Badge variant="light" size="sm">
                          JE #{c.journalEntryId}
                        </Badge>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              Issue: Dr WIP / Cr components. Complete: Dr Finished Goods / Cr WIP at rolled cost.
            </Text>
          </Stack>
        )}
      </Drawer>

      {/* Action modal (issue / complete / cancel) */}
      <Modal opened={action != null} onClose={() => setAction(null)} title={`${action ?? ''} work order`} size="md">
        <Stack>
          <LocationSelect
            label={action === 'complete' ? 'Receive finished goods into' : action === 'cancel' ? 'Return components to' : 'Issue components from'}
            locationType="STOCK"
            value={locationId}
            onChange={setLocationId}
          />
          {action === 'complete' && (
            <TextInput label="Quantity produced" value={qtyProduced} onChange={(e) => setQtyProduced(e.currentTarget.value)} />
          )}
          <DateInput label="Posting date" value={postingDate} onChange={setPostingDate} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setAction(null)}>
              Cancel
            </Button>
            <Button
              onClick={submitAction}
              loading={issue.isPending || complete.isPending || cancel.isPending}
              color={action === 'cancel' ? 'red' : undefined}
            >
              Confirm
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
