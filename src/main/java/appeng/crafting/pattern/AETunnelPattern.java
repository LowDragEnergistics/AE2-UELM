/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting.pattern;

import java.util.Objects;
import java.util.UUID;

import appeng.api.stacks.AEItemKey;

/**
 * An input-only "tunnel" pattern: it has inputs but no outputs, and is identified by a UUID.
 * <p>
 * Referencing processing patterns use a tunnel pattern item as an input; at craft time its inputs are inlined
 * (multiplied by the referenced stack size) instead of the tunnel pattern item itself. Tunnel patterns are indexed by
 * their UUID, so their contents can be changed or renamed without updating the patterns that reference them.
 */
public class AETunnelPattern extends AEProcessingPattern {

    private final UUID uuid;

    public AETunnelPattern(AEItemKey definition) {
        super(definition);
        var tag = Objects.requireNonNull(definition.getTag());

        if (ProcessingPatternEncoding.getProcessingOutputs(tag).length != 0) {
            throw new IllegalArgumentException("Not a valid tunnel pattern: input-only patterns cannot have outputs.");
        }

        this.uuid = ProcessingPatternEncoding.getTunnelUuid(tag);
        if (uuid == null) {
            throw new IllegalArgumentException("Not a valid tunnel pattern: missing or invalid tunnel UUID.");
        }
    }

    @Override
    public boolean isInputOnly() {
        return true;
    }

    @Override
    public UUID getInputOnlyUuid() {
        return uuid;
    }
}
