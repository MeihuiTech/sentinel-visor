package actorstate

import (
	"context"

	"github.com/filecoin-project/go-address"
	"github.com/filecoin-project/go-state-types/abi"
	init_ "github.com/filecoin-project/lotus/chain/actors/builtin/init"
	"golang.org/x/xerrors"

	sa0builtin "github.com/filecoin-project/specs-actors/actors/builtin"
	sa2builtin "github.com/filecoin-project/specs-actors/v2/actors/builtin"
	sa3builtin "github.com/filecoin-project/specs-actors/v3/actors/builtin"
	sa4builtin "github.com/filecoin-project/specs-actors/v4/actors/builtin"

	"github.com/filecoin-project/sentinel-visor/model"
	initmodel "github.com/filecoin-project/sentinel-visor/model/actors/init"
)

// was services/processor/tasks/init/init_actor.go

// InitExtractor extracts init actor state
type InitExtractor struct{}

func init() {
	Register(sa0builtin.InitActorCodeID, InitExtractor{})
	Register(sa2builtin.InitActorCodeID, InitExtractor{})
	Register(sa3builtin.InitActorCodeID, InitExtractor{})
	Register(sa4builtin.InitActorCodeID, InitExtractor{})
}

func (InitExtractor) Extract(ctx context.Context, a ActorInfo, node ActorStateAPI) (model.Persistable, error) {
	// genesis state.
	if a.Epoch == 0 {
		initActorState, err := init_.Load(node.Store(), &a.Actor)
		if err != nil {
			return nil, err
		}

		out := initmodel.IdAddressList{}
		if err := initActorState.ForEachActor(func(id abi.ActorID, addr address.Address) error {
			idAddr, err := address.NewIDAddress(uint64(id))
			if err != nil {
				return err
			}
			out = append(out, &initmodel.IdAddress{
				Height:    int64(a.Epoch),
				ID:        idAddr.String(),
				Address:   addr.String(),
				StateRoot: a.ParentStateRoot.String(),
			})
			return nil
		}); err != nil {
			return nil, err
		}
		return out, nil
	}
	prevActor, err := node.StateGetActor(ctx, a.Address, a.ParentTipSet.Key())
	if err != nil {
		return nil, xerrors.Errorf("loading previous init actor: %w", err)
	}

	prevState, err := init_.Load(node.Store(), prevActor)
	if err != nil {
		return nil, xerrors.Errorf("loading previous init actor state: %w", err)
	}

	curState, err := init_.Load(node.Store(), &a.Actor)
	if err != nil {
		return nil, xerrors.Errorf("loading current init actor state: %w", err)
	}

	addressChanges, err := init_.DiffAddressMap(prevState, curState)
	if err != nil {
		return nil, xerrors.Errorf("diffing init actor state: %w", err)
	}

	out := make(initmodel.IdAddressList, 0, len(addressChanges.Added)+len(addressChanges.Modified))
	for _, newAddr := range addressChanges.Added {
		out = append(out, &initmodel.IdAddress{
			StateRoot: a.ParentStateRoot.String(),
			ID:        newAddr.ID.String(),
			Address:   newAddr.PK.String(),
		})
	}
	for _, modAddr := range addressChanges.Modified {
		out = append(out, &initmodel.IdAddress{
			StateRoot: a.ParentStateRoot.String(),
			ID:        modAddr.To.ID.String(),
			Address:   modAddr.To.PK.String(),
		})
	}

	return out, nil
}
