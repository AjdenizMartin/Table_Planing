import { useRef, useState, type PointerEvent } from "react";

export interface TableLayoutDraft {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface DragStart {
  pointerId: number;
  clientX: number;
  clientY: number;
  original: TableLayoutDraft;
  latest: TableLayoutDraft;
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function clampLayout(
  layout: TableLayoutDraft,
  bounds: { width: number; height: number },
): TableLayoutDraft {
  const width = Math.min(layout.width, bounds.width);
  const height = Math.min(layout.height, bounds.height);

  return {
    x: clamp(layout.x, 0, Math.max(0, bounds.width - width)),
    y: clamp(layout.y, 0, Math.max(0, bounds.height - height)),
    width,
    height,
  };
}

function snap(value: number, gridSize: number) {
  return Math.round(value / gridSize) * gridSize;
}

export function useDraggableTable({
  tableId,
  layout,
  boardScale,
  bounds,
  snapToGrid,
  gridSize = 10,
  onSelect,
  onPreview,
  onCommit,
}: {
  tableId: number;
  layout: TableLayoutDraft;
  boardScale: number;
  bounds: { width: number; height: number };
  snapToGrid: boolean;
  gridSize?: number;
  onSelect: (tableId: number) => void;
  onPreview: (tableId: number, layout: TableLayoutDraft) => void;
  onCommit: (tableId: number, previous: TableLayoutDraft, next: TableLayoutDraft) => void;
}) {
  const dragStartRef = useRef<DragStart | null>(null);
  const [isDragging, setIsDragging] = useState(false);

  function buildNextLayout(event: PointerEvent<HTMLElement>) {
    const dragStart = dragStartRef.current;
    if (!dragStart) {
      return layout;
    }

    const deltaX = (event.clientX - dragStart.clientX) / boardScale;
    const deltaY = (event.clientY - dragStart.clientY) / boardScale;

    return clampLayout(
      {
        ...dragStart.original,
        x: Math.round(dragStart.original.x + deltaX),
        y: Math.round(dragStart.original.y + deltaY),
      },
      bounds,
    );
  }

  return {
    isDragging,
    dragHandlers: {
      onPointerDown(event: PointerEvent<HTMLElement>) {
        if (event.button !== 0) {
          return;
        }

        event.preventDefault();
        onSelect(tableId);
        event.currentTarget.setPointerCapture(event.pointerId);
        const original = clampLayout(layout, bounds);
        dragStartRef.current = {
          pointerId: event.pointerId,
          clientX: event.clientX,
          clientY: event.clientY,
          original,
          latest: original,
        };
        setIsDragging(true);
      },
      onPointerMove(event: PointerEvent<HTMLElement>) {
        const dragStart = dragStartRef.current;
        if (!dragStart || dragStart.pointerId !== event.pointerId) {
          return;
        }

        event.preventDefault();
        const nextLayout = buildNextLayout(event);
        dragStart.latest = nextLayout;
        onPreview(tableId, nextLayout);
      },
      onPointerUp(event: PointerEvent<HTMLElement>) {
        const dragStart = dragStartRef.current;
        if (!dragStart || dragStart.pointerId !== event.pointerId) {
          return;
        }

        event.preventDefault();
        event.currentTarget.releasePointerCapture(event.pointerId);

        const latest = dragStart.latest;
        const nextLayout = clampLayout(
          {
            ...latest,
            x: snapToGrid ? snap(latest.x, gridSize) : latest.x,
            y: snapToGrid ? snap(latest.y, gridSize) : latest.y,
          },
          bounds,
        );

        dragStartRef.current = null;
        setIsDragging(false);
        onPreview(tableId, nextLayout);
        onCommit(tableId, dragStart.original, nextLayout);
      },
      onPointerCancel(event: PointerEvent<HTMLElement>) {
        const dragStart = dragStartRef.current;
        if (!dragStart || dragStart.pointerId !== event.pointerId) {
          return;
        }

        event.currentTarget.releasePointerCapture(event.pointerId);
        dragStartRef.current = null;
        setIsDragging(false);
        onPreview(tableId, dragStart.original);
      },
    },
  };
}
