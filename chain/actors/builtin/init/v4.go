// Code generated by: `make actors-gen`. DO NOT EDIT.
package init

import (
	"fmt"
	"github.com/filecoin-project/go-address"
	"github.com/filecoin-project/go-state-types/abi"
	"github.com/ipfs/go-cid"
	cbg "github.com/whyrusleeping/cbor-gen"

	"github.com/filecoin-project/lotus/node/modules/dtypes"

	"github.com/filecoin-project/lily/chain/actors/adt"

	builtin4 "github.com/filecoin-project/specs-actors/v4/actors/builtin"

	init4 "github.com/filecoin-project/specs-actors/v4/actors/builtin/init"
	adt4 "github.com/filecoin-project/specs-actors/v4/actors/util/adt"
)

var _ State = (*state4)(nil)

func load4(store adt.Store, root cid.Cid) (State, error) {
	out := state4{store: store}
	err := store.Get(store.Context(), root, &out)
	if err != nil {
		return nil, err
	}
	return &out, nil
}

type state4 struct {
	init4.State
	store adt.Store
}

func (s *state4) Code() cid.Cid {
	return builtin4.InitActorCodeID
}

func (s *state4) ResolveAddress(address address.Address) (address.Address, bool, error) {
	return s.State.ResolveAddress(s.store, address)
}

func (s *state4) MapAddressToNewID(address address.Address) (address.Address, error) {
	return s.State.MapAddressToNewID(s.store, address)
}

func (s *state4) ForEachActor(cb func(id abi.ActorID, address address.Address) error) error {
	addrs, err := adt4.AsMap(s.store, s.State.AddressMap, builtin4.DefaultHamtBitwidth)
	if err != nil {
		return err
	}
	var actorID cbg.CborInt
	return addrs.ForEach(&actorID, func(key string) error {
		addr, err := address.NewFromBytes([]byte(key))
		if err != nil {
			return err
		}
		return cb(abi.ActorID(actorID), addr)
	})
}

func (s *state4) NetworkName() (dtypes.NetworkName, error) {
	return dtypes.NetworkName(s.State.NetworkName), nil
}

func (s *state4) SetNetworkName(name string) error {
	s.State.NetworkName = name
	return nil
}

func (s *state4) Remove(addrs ...address.Address) (err error) {
	m, err := adt4.AsMap(s.store, s.State.AddressMap, builtin4.DefaultHamtBitwidth)
	if err != nil {
		return err
	}
	for _, addr := range addrs {
		if err = m.Delete(abi.AddrKey(addr)); err != nil {
			return fmt.Errorf("failed to delete entry for address: %s; err: %w", addr, err)
		}
	}
	amr, err := m.Root()
	if err != nil {
		return fmt.Errorf("failed to get address map root: %w", err)
	}
	s.State.AddressMap = amr
	return nil
}

func (s *state4) addressMap() (adt.Map, error) {
	return adt4.AsMap(s.store, s.AddressMap, builtin4.DefaultHamtBitwidth)
}
