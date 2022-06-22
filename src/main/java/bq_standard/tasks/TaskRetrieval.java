package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.IItemTask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.client.gui.tasks.PanelTaskRetrieval;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.base.TaskProgressableBase;
import bq_standard.tasks.factory.FactoryTaskRetrieval;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

public class TaskRetrieval extends TaskProgressableBase<int[]> implements ITaskInventory, IItemTask
{
	public final List<BigItemStack> requiredItems = new ArrayList<>();
	public boolean partialMatch = true;
	public boolean ignoreNBT = true;
	public boolean consume = false;
	public boolean groupDetect = false;
	public boolean autoConsume = false;
	
	@Override
	public String getUnlocalisedName()
	{
		return BQ_Standard.MODID + ".task.retrieval";
	}
	
	@Override
	public ResourceLocation getFactoryID()
	{
		return FactoryTaskRetrieval.INSTANCE.getRegistryName();
	}
	
	@Override
	public void onInventoryChange(@Nonnull DBEntry<IQuest> quest, @Nonnull ParticipantInfo pInfo)
    {
        if(!consume || autoConsume)
        {
            detect(pInfo, quest);
        }
    }
    

    @Override
    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        if (isComplete(pInfo.UUID)) return;

        Detector detector = new Detector(this, consume ? Collections.singletonList(pInfo.UUID) : pInfo.ALL_UUIDS);

        final List<InventoryPlayer> invoList;
        if (consume) {
            invoList = Collections.singletonList(pInfo.PLAYER.inventory);
        } else {
            invoList = new ArrayList<>(pInfo.ACTIVE_PLAYERS.size());
            pInfo.ACTIVE_PLAYERS.forEach((p) -> invoList.add(p.inventory));
        }

        for (InventoryPlayer invo : invoList) {
            IntStream.range(0, invo.getSizeInventory()).forEachOrdered(i -> {
                ItemStack stack = invo.getStackInSlot(i);
                detector.run(stack, remaining -> invo.decrStackSize(i, remaining));
            });
        }

        if (detector.updated) setBulkProgress(detector.progress);
        checkAndComplete(pInfo, quest, detector.updated, detector.progress);
    }
	
	private void checkAndComplete(ParticipantInfo pInfo, DBEntry<IQuest> quest, boolean resync, List<Tuple2<UUID, int[]>> progress)
    {
        boolean updated = resync;
        
        topLoop:
        for(Tuple2<UUID, int[]> value : progress)
        {
            for(int j = 0; j < requiredItems.size(); j++)
            {
                if(value.getSecond()[j] >= requiredItems.get(j).stackSize) continue;
                continue topLoop;
            }
            
            updated = true;
            
            if(consume)
            {
                setComplete(value.getFirst());
            } else
            {
                progress.forEach((pair) -> setComplete(pair.getFirst()));
                break;
            }
        }
		
		if(updated)
        {
            if(consume)
            {
                pInfo.markDirty(Collections.singletonList(quest.getID()));
            } else
            {
                pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
            }
        }
    }

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt)
	{
		nbt.setBoolean("partialMatch", partialMatch);
		nbt.setBoolean("ignoreNBT", ignoreNBT);
		nbt.setBoolean("consume", consume);
		nbt.setBoolean("groupDetect", groupDetect);
		nbt.setBoolean("autoConsume", autoConsume);
		
		NBTTagList itemArray = new NBTTagList();
		for(BigItemStack stack : this.requiredItems)
		{
			itemArray.appendTag(JsonHelper.ItemStackToJson(stack, new NBTTagCompound()));
		}
		nbt.setTag("requiredItems", itemArray);
		
		return nbt;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		partialMatch = nbt.getBoolean("partialMatch");
		ignoreNBT = nbt.getBoolean("ignoreNBT");
		consume = nbt.getBoolean("consume");
		groupDetect = nbt.getBoolean("groupDetect");
		autoConsume = nbt.getBoolean("autoConsume");
		
		requiredItems.clear();
		NBTTagList iList = nbt.getTagList("requiredItems", 10);
		for(int i = 0; i < iList.tagCount(); i++)
		{
			requiredItems.add(JsonHelper.JsonToItemStack(iList.getCompoundTagAt(i)));
		}
	}

	@Override
	public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest)
	{
	    return new PanelTaskRetrieval(rect, this);
	}
	
	@Override
	public boolean canAcceptItem(UUID owner, DBEntry<IQuest> quest, ItemStack stack)
	{
		if(owner == null || stack == null || stack.stackSize <= 0 || !consume || isComplete(owner) || requiredItems.size() <= 0)
		{
			return false;
		}
		
		int[] progress = getUsersProgress(owner);
		
		for(int j = 0; j < requiredItems.size(); j++)
		{
			BigItemStack rStack = requiredItems.get(j);
			
			if(progress[j] >= rStack.stackSize) continue;
			
			if(ItemComparison.StackMatch(rStack.getBaseStack(), stack, !ignoreNBT, partialMatch) || ItemComparison.OreDictionaryMatch(rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !ignoreNBT, partialMatch))
			{
				return true;
			}
		}
		
		return false;
	}

    @Override
    public ItemStack submitItem(UUID owner, DBEntry<IQuest> quest, ItemStack input) {
        if (owner == null || input == null || !consume || isComplete(owner)) return input;

        Detector detector = new Detector(this, Collections.singletonList(owner));

        final ItemStack stack = input.copy();

        detector.run(stack, (remaining) -> {
            int removed = Math.min(stack.stackSize, remaining);
            return stack.splitStack(removed);
        });

        if (detector.updated) {
            setBulkProgress(detector.progress);
        }

        return 0 < stack.stackSize ? stack : null;
    }

    @Override
    public void retrieveItems(ParticipantInfo pInfo, DBEntry<IQuest> quest, ItemStack[] stacks) {
        if (consume || isComplete(pInfo.UUID)) return;

        Detector detector = new Detector(this, consume ? Collections.singletonList(pInfo.UUID) : pInfo.ALL_UUIDS);

        for (ItemStack stack : stacks) {
            detector.run(stack, (remaining) -> null); // Never execute consumer
        }

        if (detector.updated) setBulkProgress(detector.progress);
        checkAndComplete(pInfo, quest, detector.updated, detector.progress);
    }

	@Override
	@SideOnly(Side.CLIENT)
	public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest)
	{
		return null;
	}

    @Override
    public int[] getUsersProgress(UUID uuid) {
        int[] progress = userProgress.get(uuid);
        return progress == null || progress.length != requiredItems.size() ? new int[requiredItems.size()] : progress;
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public int[] readUserProgressFromNBT(NBTTagCompound nbt) {
        // region Legacy
        if (nbt.hasKey("data", Constants.NBT.TAG_LIST)) {
            int[] data = new int[requiredItems.size()];
            List<NBTBase> dNbt = NBTConverter.getTagList(nbt.getTagList("data", Constants.NBT.TAG_INT));
            for (int i = 0; i < data.length && i < dNbt.size(); i++) {
                data[i] = ((NBTPrimitive) dNbt.get(i)).func_150287_d();
            }
            return data;
        }
        // endregion
        final int[] data = nbt.getIntArray("data");
        final int[] progress = new int[requiredItems.size()];
        System.arraycopy(data, 0, progress, 0, Math.min(data.length, progress.length));
        return progress;
    }

    @Override
    public void writeUserProgressToNBT(NBTTagCompound nbt, int[] progress) {
        nbt.setIntArray("data", progress);
    }

	@Override
	public List<String> getTextsForSearch() {
		List<String> texts = new ArrayList<>();
		for (BigItemStack bigStack : requiredItems) {
			ItemStack stack = bigStack.getBaseStack();
			texts.add(stack.getDisplayName());
			if (bigStack.hasOreDict()) {
				texts.add(bigStack.getOreDict());
			}
		}
		return texts;
	}

    static class Detector {
        public boolean updated = false;
        public final TaskRetrieval task;
        /**
         * List of (player uuid, [progress per required item])
         */
        public final List<Tuple2<UUID, int[]>> progress;
        private final int[] remCounts;

        public Detector(TaskRetrieval task, @Nonnull List<UUID> uuids) {
            this.task = task;
            this.progress = task.getBulkProgress(uuids);
            this.remCounts = new int[progress.size()];
            if (!task.consume) {
                if (task.groupDetect) {
                    // Reset all detect progress
                    progress.forEach((value) -> Arrays.fill(value.getSecond(), 0));
                } else {
                    for (int i = 0; i < task.requiredItems.size(); i++) {
                        final int r = task.requiredItems.get(i).stackSize;
                        for (Tuple2<UUID, int[]> value : progress) {
                            int n = value.getSecond()[i];
                            if (n != 0 && n < r) {
                                value.getSecond()[i] = 0;
                                this.updated = true;
                            }
                        }
                    }
                }
            }
        }

        /**
         * @param consumer
         *     Args: (remaining)
         */
        public void run(ItemStack stack, IntFunction<ItemStack> consumer) {
            if (stack == null || stack.stackSize <= 0) return;
            // Allows the stack detection to split across multiple requirements. Counts may vary per person
            Arrays.fill(remCounts, stack.stackSize);

            for (int i = 0; i < task.requiredItems.size(); i++) {
                BigItemStack rStack = task.requiredItems.get(i);

                if (!ItemComparison.StackMatch(rStack.getBaseStack(), stack, !task.ignoreNBT, task.partialMatch)
                    && !ItemComparison.OreDictionaryMatch(rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !task.ignoreNBT, task.partialMatch)) {
                    continue;
                }

                for (int n = 0; n < progress.size(); n++) {
                    Tuple2<UUID, int[]> value = progress.get(n);
                    if (value.getSecond()[i] >= rStack.stackSize) continue;

                    int remaining = rStack.stackSize - value.getSecond()[i];

                    if (task.consume) {
                        ItemStack removed = consumer.apply(remaining);
                        value.getSecond()[i] += removed.stackSize;
                    } else {
                        int temp = Math.min(remaining, remCounts[n]);
                        remCounts[n] -= temp;
                        value.getSecond()[i] += temp;
                    }
                    updated = true;
                }
            }
        }
    }
}
