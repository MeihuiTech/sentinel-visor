package indexer

import (
	"container/list"
	"context"

	"github.com/filecoin-project/lotus/chain/types"
	pg "github.com/go-pg/pg/v10"
	"go.opentelemetry.io/otel/api/global"
	"go.opentelemetry.io/otel/api/trace"
	"go.opentelemetry.io/otel/label"
	"golang.org/x/xerrors"

	"github.com/filecoin-project/sentinel-visor/lens"
	"github.com/filecoin-project/sentinel-visor/storage"
)

func NewChainHistoryIndexer(d *storage.Database, node lens.API) *ChainHistoryIndexer {
	return &ChainHistoryIndexer{
		node:    node,
		storage: d,

		// TODO base finality value on the spec: https://github.com/filecoin-project/specs-actors/pull/702
		finality: 1400,

		batchSize: 500,
	}
}

// ChainHistoryIndexer is a task that indexes blocks by following the chain history.
type ChainHistoryIndexer struct {
	node      lens.API
	storage   *storage.Database
	finality  int // epochs after which chain state is considered final
	batchSize int // number of blocks to persist in a batch
}

// Run starts walking the chain history and continues until the context is done or
// the start of the chain is reached.
func (c *ChainHistoryIndexer) Run(ctx context.Context) error {
	height, err := c.mostRecentlySyncedBlockHeight(ctx)
	if err != nil {
		return xerrors.Errorf("get synced block height: %w", err)
	}

	if err := c.WalkChain(ctx, height); err != nil {
		return xerrors.Errorf("collect blocks: %w", err)
	}

	return nil
}

func (c *ChainHistoryIndexer) WalkChain(ctx context.Context, maxHeight int64) error {
	ctx, span := global.Tracer("").Start(ctx, "ChainHistoryIndexer.WalkChain", trace.WithAttributes(label.Int64("height", maxHeight)))
	defer span.End()

	// get at most finality tipsets not exceeding maxHeight. These are blocks we have in the database but have not processed.
	// Now we are going to walk down the chain from `head` until we have visited all blocks not in the database.
	initialTipSets, err := c.storage.UnprocessedIndexedTipSets(ctx, int(maxHeight), c.finality)
	if err != nil {
		return xerrors.Errorf("get unprocessed blocks: %w", err)
	}
	log.Debugw("collect initial unprocessed tipsets", "count", len(initialTipSets))

	// Data extracted from tipsets and block headers awaiting persistence
	blockData := NewUnindexedBlockData()

	// A queue of tipsets that are yet to be visited
	toVisit := list.New()

	// Mark all visited blocks from the database as already seen
	for _, t := range initialTipSets {
		tsk, err := t.TipSetKey()
		if err != nil {
			return xerrors.Errorf("decode tipsetkey: %w", err)
		}
		blockData.MarkSeen(tsk)
	}

	// walk backwards from head until we find a block that we have
	head, err := c.node.ChainHead(ctx)
	if err != nil {
		return xerrors.Errorf("get chain head: %w", err)
	}

	toVisit.PushBack(head)

	for toVisit.Len() > 0 {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		ts := toVisit.Remove(toVisit.Back()).(*types.TipSet)
		if blockData.Seen(ts.Key()) {
			continue
		}

		blockData.AddTipSet(ts)

		if blockData.Size() >= c.batchSize {
			log.Debugw("persisting batch", "count", blockData.Size(), "queued", toVisit.Len(), "current_height", ts.Height())
			// persist the batch of blocks to storage
			if err := blockData.Persist(ctx, c.storage.DB); err != nil {
				return xerrors.Errorf("persist: %w", err)
			}
			blockData.Reset()
		}

		if ts.Height() == 0 {
			continue
		}

		pts, err := c.node.ChainGetTipSet(ctx, ts.Parents())
		if err != nil {
			return xerrors.Errorf("get tipset: %w", err)
		}

		toVisit.PushBack(pts)
	}

	log.Debugw("persisting final batch", "count", blockData.Size(), "toVisit", toVisit.Len())
	if err := blockData.Persist(ctx, c.storage.DB); err != nil {
		return xerrors.Errorf("persist: %w", err)
	}

	return nil
}

func (c *ChainHistoryIndexer) mostRecentlySyncedBlockHeight(ctx context.Context) (int64, error) {
	ctx, span := global.Tracer("").Start(ctx, "ChainHistoryIndexer.mostRecentlySyncedBlockHeight")
	defer span.End()

	recent, err := c.storage.MostRecentSyncedBlock(ctx)
	if err != nil {
		if err == pg.ErrNoRows {
			return 0, nil
		}
		return 0, xerrors.Errorf("query recent synced: %w", err)
	}
	return recent.Height, nil
}
