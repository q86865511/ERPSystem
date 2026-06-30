import { useState } from 'react';
import { Badge, Button, Group, Modal, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreateWorkOrderRequest } from '../../api/types';
import { ItemSelect, LocationSelect } from '../../components/EntitySelect';
import { DataTable, DetailDrawer, MoneyText, StateButton, StatusBadge } from '../../components';
import type { DataTableColumn } from '../../components';
import { useItemMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
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
  const { t } = useI18n();
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
    .map((b) => ({
      value: String(b.id),
      label: t('manufacturing.wo.bomOption', { version: b.version ?? '', outputQty: b.outputQty ?? '' }),
    }));

  const openCreate = () => {
    setItemId(null);
    setBomId(null);
    setQtyToProduce('1');
    open();
  };

  const submitCreate = async () => {
    if (!itemId || !bomId || !qtyToProduce) {
      notifyError(t('manufacturing.wo.pickItemBomQty'));
      return;
    }
    const body: CreateWorkOrderRequest = { itemId, bomId, qtyToProduce };
    try {
      const wo = await create.mutateAsync(body);
      notifySuccess(t('manufacturing.wo.created', { woNumber: wo?.woNumber ?? '' }));
      close();
      if (wo?.id != null) setDetailId(wo.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const doRelease = async (id: number) => {
    try {
      await release.mutateAsync(id);
      notifySuccess(t('manufacturing.wo.released'));
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
      notifyError(t('manufacturing.wo.pickStockLocation'));
      return;
    }
    try {
      if (action === 'issue') {
        await issue.mutateAsync({ id: detailId, stockLocationId: locationId, postingDate: postingDate ?? undefined });
        notifySuccess(t('manufacturing.wo.componentsIssued'));
      } else if (action === 'complete') {
        if (!qtyProduced) {
          notifyError(t('manufacturing.wo.enterQtyProduced'));
          return;
        }
        await complete.mutateAsync({
          id: detailId,
          qtyProduced,
          stockLocationId: locationId,
          postingDate: postingDate ?? undefined,
        });
        notifySuccess(t('manufacturing.wo.completed'));
      } else if (action === 'cancel') {
        await cancel.mutateAsync({ id: detailId, stockLocationId: locationId, postingDate: postingDate ?? undefined });
        notifySuccess(t('manufacturing.wo.cancelled'));
      }
      setAction(null);
    } catch (e) {
      notifyError(e);
    }
  };

  const status = detail.data?.status;
  const rows = list.data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'woNumber', label: t('manufacturing.wo.woNumber') },
    {
      key: 'item',
      label: t('field.item'),
      render: (w) => (w.itemId != null ? (items.get(w.itemId) ?? w.itemId) : '—'),
    },
    { key: 'qtyToProduce', label: t('manufacturing.wo.toProduce'), align: 'right' },
    { key: 'qtyProduced', label: t('manufacturing.wo.produced'), align: 'right' },
    { key: 'status', label: t('field.status'), render: (w) => <StatusBadge status={w.status} /> },
  ];

  return (
    <Stack>
      {writable && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openCreate}>
            {t('manufacturing.wo.newWo')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(w) => w.id ?? w.woNumber ?? ''}
        isLoading={list.isLoading}
        emptyMessage={t('manufacturing.wo.empty')}
        onRowClick={(w) => setDetailId(w.id ?? null)}
        minWidth={640}
      />

      {/* Create */}
      <Modal opened={opened} onClose={close} title={t('manufacturing.wo.newWo')} size="md">
        <Stack>
          <ItemSelect
            label={t('manufacturing.wo.finishedItem')}
            itemType="FINISHED"
            value={itemId}
            onChange={(id) => {
              setItemId(id);
              setBomId(null);
            }}
          />
          <Select
            label={t('manufacturing.wo.bom')}
            placeholder={t('manufacturing.wo.bomPlaceholder')}
            data={bomOptions}
            value={bomId != null ? String(bomId) : null}
            onChange={(v) => setBomId(v ? Number(v) : null)}
          />
          <TextInput label={t('manufacturing.wo.qtyToProduce')} value={qtyToProduce} onChange={(e) => setQtyToProduce(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submitCreate} loading={create.isPending}>
              {t('common.create')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      {/* Detail with state-machine actions */}
      <DetailDrawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        title={t('manufacturing.wo.drawerTitle', { woNumber: detail.data?.woNumber ?? '' })}
        footer={
          writable && detail.data ? (
            <Group>
              <StateButton
                label={t('manufacturing.wo.release')}
                disabled={status !== 'DRAFT'}
                disabledReason={t('manufacturing.wo.releaseHint')}
                loading={release.isPending}
                onClick={() => {
                  if (detailId != null) void doRelease(detailId);
                }}
              />
              <StateButton
                label={t('manufacturing.wo.issue')}
                disabled={status !== 'RELEASED'}
                disabledReason={t('manufacturing.wo.issueHint')}
                onClick={() => openAction('issue')}
              />
              <StateButton
                label={t('manufacturing.wo.complete')}
                disabled={status !== 'IN_PROGRESS'}
                disabledReason={t('manufacturing.wo.completeHint')}
                onClick={() => openAction('complete')}
              />
              <StateButton
                label={t('common.cancel')}
                color="red"
                variant="light"
                disabled={status !== 'RELEASED' && status !== 'IN_PROGRESS'}
                disabledReason={t('manufacturing.wo.cancelHint')}
                onClick={() => openAction('cancel')}
              />
            </Group>
          ) : undefined
        }
      >
        {detail.data && (
          <>
            <Group justify="space-between">
              <Group>
                <StatusBadge status={status} />
                <Text size="sm" c="dimmed">
                  {t('manufacturing.wo.produceSummary', {
                    item: (detail.data.itemId != null ? items.get(detail.data.itemId) : '') ?? '',
                    qty: detail.data.qtyToProduce ?? '',
                  })}
                </Text>
              </Group>
              <Text size="sm">
                {t('manufacturing.wo.componentCost')} <MoneyText value={detail.data.totalComponentCost} />
              </Text>
            </Group>

            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('manufacturing.bom.component')}</Table.Th>
                  <Table.Th ta="right">{t('manufacturing.wo.planned')}</Table.Th>
                  <Table.Th ta="right">{t('manufacturing.wo.consumed')}</Table.Th>
                  <Table.Th ta="right">{t('manufacturing.wo.value')}</Table.Th>
                  <Table.Th>{t('manufacturing.wo.posting')}</Table.Th>
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
                          {t('manufacturing.wo.jeBadge', { id: c.journalEntryId })}
                        </Badge>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              {t('manufacturing.wo.postingHint')}
            </Text>
          </>
        )}
      </DetailDrawer>

      {/* Action modal (issue / complete / cancel) */}
      <Modal
        opened={action != null}
        onClose={() => setAction(null)}
        title={t('manufacturing.wo.actionTitle', {
          action:
            action === 'issue'
              ? t('manufacturing.wo.actionIssue')
              : action === 'complete'
                ? t('manufacturing.wo.actionComplete')
                : action === 'cancel'
                  ? t('manufacturing.wo.actionCancel')
                  : '',
        })}
        size="md"
      >
        <Stack>
          <LocationSelect
            label={
              action === 'complete'
                ? t('manufacturing.wo.receiveInto')
                : action === 'cancel'
                  ? t('manufacturing.wo.returnTo')
                  : t('manufacturing.wo.issueFrom')
            }
            locationType="STOCK"
            value={locationId}
            onChange={setLocationId}
          />
          {action === 'complete' && (
            <TextInput label={t('manufacturing.wo.quantityProduced')} value={qtyProduced} onChange={(e) => setQtyProduced(e.currentTarget.value)} />
          )}
          <DateInput label={t('manufacturing.wo.postingDate')} value={postingDate} onChange={setPostingDate} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setAction(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              onClick={submitAction}
              loading={issue.isPending || complete.isPending || cancel.isPending}
              color={action === 'cancel' ? 'red' : undefined}
            >
              {t('common.confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
