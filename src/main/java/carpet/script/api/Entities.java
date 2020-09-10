package carpet.script.api;

import carpet.script.CarpetContext;
import carpet.script.Expression;
import carpet.script.LazyValue;
import carpet.script.argument.Vector3Argument;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.EntityValue;
import carpet.script.value.FunctionValue;
import carpet.script.value.ListValue;
import carpet.script.value.NBTSerializableValue;
import carpet.script.value.NullValue;
import carpet.script.value.NumericValue;
import carpet.script.value.Value;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Entities {
    public static void apply(Expression expression)
    {
        expression.addLazyFunction("player", -1, (c, t, lv) ->
        {
            if (lv.size() ==0)
            {

                Entity callingEntity = ((CarpetContext)c).s.getEntity();
                if (callingEntity instanceof PlayerEntity)
                {
                    Value retval = new EntityValue(callingEntity);
                    return (_c, _t) -> retval;
                }
                Vec3d pos = ((CarpetContext)c).s.getPosition();
                PlayerEntity closestPlayer = ((CarpetContext)c).s.getWorld().getClosestPlayer(pos.x, pos.y, pos.z, -1.0, EntityPredicates.VALID_ENTITY);
                if (closestPlayer != null)
                {
                    Value retval = new EntityValue(closestPlayer);
                    return (_c, _t) -> retval;
                }
                return (_c, _t) -> Value.NULL;
            }
            String playerName = lv.get(0).evalValue(c).getString();
            Value retval = Value.NULL;
            if ("all".equalsIgnoreCase(playerName))
            {
                retval = ListValue.wrap(
                        ((CarpetContext)c).s.getMinecraftServer().getPlayerManager().getPlayerList().
                                stream().map(EntityValue::new).collect(Collectors.toList()));
            }
            else if ("*".equalsIgnoreCase(playerName))
            {
                retval = ListValue.wrap(
                        ((CarpetContext)c).s.getWorld().getPlayers().
                                stream().map(EntityValue::new).collect(Collectors.toList()));
            }
            else if ("survival".equalsIgnoreCase(playerName))
            {
                retval =  ListValue.wrap(
                        ((CarpetContext)c).s.getWorld().getPlayers((p) -> p.interactionManager.isSurvivalLike()).
                                stream().map(EntityValue::new).collect(Collectors.toList()));
            }
            else if ("creative".equalsIgnoreCase(playerName))
            {
                retval = ListValue.wrap(
                        ((CarpetContext)c).s.getWorld().getPlayers(PlayerEntity::isCreative).
                                stream().map(EntityValue::new).collect(Collectors.toList()));
            }
            else if ("spectating".equalsIgnoreCase(playerName))
            {
                retval = ListValue.wrap(
                        ((CarpetContext)c).s.getWorld().getPlayers(PlayerEntity::isSpectator).
                                stream().map(EntityValue::new).collect(Collectors.toList()));
            }
            else if ("!spectating".equalsIgnoreCase(playerName))
            {
                retval = ListValue.wrap(
                        ((CarpetContext)c).s.getWorld().getPlayers((p) -> !p.isSpectator()).
                                stream().map(EntityValue::new).collect(Collectors.toList()));
            }
            else
            {
                ServerPlayerEntity player = ((CarpetContext) c).s.getMinecraftServer().getPlayerManager().getPlayer(playerName);
                if (player != null)
                    retval = new EntityValue(player);
            }
            Value finalVar = retval;
            return (cc, tt) -> finalVar;
        });

        expression.addLazyFunction("spawn", -1, (c, t, lv) ->
        {
            CarpetContext cc = (CarpetContext)c;
            if (lv.size() < 2)
                throw new InternalExpressionException("'spawn' function takes mob name, and position to spawn");
            String entityString = lv.get(0).evalValue(c).getString();
            Identifier entityId;
            try
            {
                entityId = Identifier.fromCommandInput(new StringReader(entityString));
                EntityType type = Registry.ENTITY_TYPE.getOrEmpty(entityId).orElse(null);
                if (type == null || !type.isSummonable())
                    return LazyValue.NULL;
            }
            catch (CommandSyntaxException exception)
            {
                 return LazyValue.NULL;
            }

            Vector3Argument position = Vector3Argument.findIn(cc, lv, 1);
            if (position.fromBlock)
                position.vec = position.vec.subtract(0, 0.5, 0);
            CompoundTag tag = new CompoundTag();
            boolean hasTag = false;
            if (lv.size() > position.offset)
            {
                Value nbt = lv.get(position.offset).evalValue(c);
                NBTSerializableValue v = (nbt instanceof NBTSerializableValue) ? (NBTSerializableValue) nbt
                        : NBTSerializableValue.parseString(nbt.getString());
                hasTag = true;
                tag = v.getCompoundTag();

            }
            tag.putString("id", entityId.toString());
            Vec3d vec3d = position.vec;

            ServerWorld serverWorld = cc.s.getWorld();
            Entity entity_1 = EntityType.loadEntityWithPassengers(tag, serverWorld, (entity_1x) -> {
                entity_1x.refreshPositionAndAngles(vec3d.x, vec3d.y, vec3d.z, entity_1x.yaw, entity_1x.pitch);
                return !serverWorld.tryLoadEntity(entity_1x) ? null : entity_1x;
            });
            if (entity_1 == null) {
                return LazyValue.NULL;
            } else {
                if (!hasTag && entity_1 instanceof MobEntity) {
                    ((MobEntity)entity_1).initialize(serverWorld, serverWorld.getLocalDifficulty(entity_1.getBlockPos()), SpawnReason.COMMAND, null, null);
                }
                Value res = new EntityValue(entity_1);
                return (_c, _t) -> res;
            }
        });

        expression.addLazyFunction("entity_id", 1, (c, t, lv) ->
        {
            Value who = lv.get(0).evalValue(c);
            Entity e;
            if (who instanceof NumericValue)
            {
                e = ((CarpetContext)c).s.getWorld().getEntityById((int)((NumericValue) who).getLong());
            }
            else
            {
                e = ((CarpetContext)c).s.getWorld().getEntity(UUID.fromString(who.getString()));
            }
            if (e==null)
            {
                return LazyValue.NULL;
            }
            return (cc, tt) -> new EntityValue(e);
        });

        expression.addLazyFunction("entity_list", 1, (c, t, lv) ->
        {
            String who = lv.get(0).evalValue(c).getString();
            Pair<EntityType<?>, Predicate<? super Entity>> pair = EntityValue.getPredicate(who);
            if (pair == null)
            {
                throw new InternalExpressionException("Unknown entity selection criterion: "+who);
            }
            List<Entity> entityList = ((CarpetContext)c).s.getWorld().getEntitiesByType(pair.getKey(), pair.getValue());
            Value retval = ListValue.wrap(entityList.stream().map(EntityValue::new).collect(Collectors.toList()));
            return (_c, _t ) -> retval;
        });

        expression.addLazyFunction("entity_area", 7, (c, t, lv) ->
        {
            Vec3d center = new Vec3d(
                    NumericValue.asNumber(lv.get(1).evalValue(c)).getDouble(),
                    NumericValue.asNumber(lv.get(2).evalValue(c)).getDouble(),
                    NumericValue.asNumber(lv.get(3).evalValue(c)).getDouble()
            );
            Box area = new Box(center, center).expand(
                    NumericValue.asNumber(lv.get(4).evalValue(c)).getDouble(),
                    NumericValue.asNumber(lv.get(5).evalValue(c)).getDouble(),
                    NumericValue.asNumber(lv.get(6).evalValue(c)).getDouble()
            );
            String who = lv.get(0).evalValue(c).getString();
            Pair<EntityType<?>, Predicate<? super Entity>> pair = EntityValue.getPredicate(who);
            if (pair == null)
            {
                throw new InternalExpressionException("Unknown entity selection criterion: "+who);
            }
            List<Entity> entityList = ((CarpetContext)c).s.getWorld().getEntitiesByType((EntityType<Entity>) pair.getKey(), area, pair.getValue());
            Value retval = ListValue.wrap(entityList.stream().map(EntityValue::new).collect(Collectors.toList()));
            return (_c, _t ) -> retval;
        });

        expression.addLazyFunction("entity_selector", -1, (c, t, lv) ->
        {
            String selector = lv.get(0).evalValue(c).getString();
            List<Value> retlist = new ArrayList<>();
            for (Entity e: EntityValue.getEntitiesFromSelector(((CarpetContext)c).s, selector))
            {
                retlist.add(new EntityValue(e));
            }
            return (c_, t_) -> ListValue.wrap(retlist);
        });

        expression.addLazyFunction("query", -1, (c, t, lv) ->
        {
            if (lv.size()<2)
            {
                throw new InternalExpressionException("'query' takes entity as a first argument, and queried feature as a second");
            }
            Value v = lv.get(0).evalValue(c);
            if (!(v instanceof EntityValue))
                throw new InternalExpressionException("First argument to query should be an entity");
            String what = lv.get(1).evalValue(c).getString();
            Value retval;
            if (lv.size()==2)
                retval = ((EntityValue) v).get(what, null);
            else if (lv.size()==3)
                retval = ((EntityValue) v).get(what, lv.get(2).evalValue(c));
            else
                retval = ((EntityValue) v).get(what, ListValue.wrap(lv.subList(2, lv.size()).stream().map((vv) -> vv.evalValue(c)).collect(Collectors.toList())));
            return (cc, tt) -> retval;
        });

        // or update
        expression.addLazyFunction("modify", -1, (c, t, lv) ->
        {
            if (lv.size()<2)
            {
                throw new InternalExpressionException("'modify' takes entity as a first argument, and queried feature as a second");
            }
            Value v = lv.get(0).evalValue(c);
            if (!(v instanceof EntityValue))
                throw new InternalExpressionException("First argument to modify should be an entity");
            String what = lv.get(1).evalValue(c).getString();
            if (lv.size()==2)
                ((EntityValue) v).set(what, null);
            else if (lv.size()==3)
                ((EntityValue) v).set(what, lv.get(2).evalValue(c));
            else
                ((EntityValue) v).set(what, ListValue.wrap(lv.subList(2, lv.size()).stream().map((vv) -> vv.evalValue(c)).collect(Collectors.toList())));
            return (cc, tt) -> v;
        });

        // or update
        expression.addLazyFunction("entity_event", -1, (c, t, lv) ->
        {
            if (lv.size()<3)
                throw new InternalExpressionException("'entity_event' requires at least 3 arguments, entity, event to be handled, and function name, with optional arguments");
            Value v = lv.get(0).evalValue(c);
            if (!(v instanceof EntityValue))
                throw new InternalExpressionException("First argument to entity_event should be an entity");
            String what = lv.get(1).evalValue(c).getString();
            Value functionValue = lv.get(2).evalValue(c);
            if (functionValue instanceof NullValue)
                functionValue = null;
            else if (!(functionValue instanceof FunctionValue))
            {
                String name = functionValue.getString();
                functionValue = c.host.getAssertFunction(expression.module, name);
            }
            FunctionValue function = (FunctionValue)functionValue;
            List<Value> args = null;
            if (lv.size()==4)
                args = Collections.singletonList(lv.get(3).evalValue(c));
            else if (lv.size()>4)
            {
                args = lv.subList(3, lv.size()).stream().map((vv) -> vv.evalValue(c)).collect(Collectors.toList());
            }

            ((EntityValue) v).setEvent((CarpetContext)c, what, function, args);

            return LazyValue.NULL;
        });
    }
}