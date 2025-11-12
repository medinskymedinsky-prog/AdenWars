package net.sf.l2j.gameserver.model.actor.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.serverpackets.AutoAttackStart;
import net.sf.l2j.gameserver.enums.items.ItemLocation;

public class AutoFarmManager
{
    private static AutoFarmManager _instance;
    private final Map<Integer, ScheduledFuture<?>> _farmTasks = new ConcurrentHashMap<>();
    
    public static AutoFarmManager getInstance()
    {
        if (_instance == null)
            _instance = new AutoFarmManager();
        return _instance;
    }
    
    /**
     * Запуск автофарма для игрока
     */
    public void startAutoFarm(Player player, int radius)
    {
        stopAutoFarm(player); // Останавливаем предыдущую задачу
        
        player.setAutoFarm(true);
        player.setFarmRadius(radius);
        
        // Запускаем задачу автофарма
        ScheduledFuture<?> task = ThreadPool.scheduleAtFixedRate(new AutoFarmTask(player), 1000, 1000); // Каждую секунду
        _farmTasks.put(player.getObjectId(), task);
        
        player.sendMessage("🤖 Автофарм запущен! Радиус: " + radius + "px");
    }
    
    /**
     * Остановка автофарма для игрока
     */
    public void stopAutoFarm(Player player)
    {
        player.setAutoFarm(false);
        
        ScheduledFuture<?> task = _farmTasks.remove(player.getObjectId());
        if (task != null)
            task.cancel(false);
        
        player.sendMessage("⏹️ Автофарм остановлен");
    }
    
    /**
     * Задача автофарма
     */
    private class AutoFarmTask implements Runnable
    {
        private final Player _player;
        
        public AutoFarmTask(Player player)
        {
            _player = player;
        }
        
        @Override
        public void run()
        {
            try
            {
                if (_player == null || _player.isDead() || !_player.isOnline() || !_player.isAutoFarm())
                {
                    stopAutoFarm(_player);
                    return;
                }
                
                // Если игрок в бою - ждем окончания
                if (_player.isInCombat())
                    return;
                
                // 1. Поиск мобов для атаки
                Attackable target = findAttackableTarget();
                if (target != null)
                {
                    attackTarget(target);
                    return;
                }
                
                // 2. Авто-подбор лута
                autoLoot();
                
                // 3. Авто-хил
                autoHeal();
                
            }
            catch (Exception e)
            {
                // Игнорируем ошибки чтобы задача не падала
            }
        }
        
        /**
         * Поиск целей для атаки
         */
        private Attackable findAttackableTarget()
        {
            for (Creature creature : World.getInstance().getAroundCharacters(_player, Creature.class))
            {
                if (creature instanceof Attackable attackable && 
                    !creature.isDead() && 
                    _player.isIn3DRadius(creature, _player.getFarmRadius()) &&
                    !creature.isInCombat() &&
                    creature.getTarget() == null)
                {
                    // Проверяем уровень моба (не атакуем слишком сильных)
                    if (Math.abs(creature.getStatus().getLevel() - _player.getStatus().getLevel()) <= 10)
                        return attackable;
                }
            }
            return null;
        }
        
        /**
         * Атака цели
         */
        private void attackTarget(Attackable target)
        {
            if (_player.getTarget() != target)
                _player.setTarget(target);
            
            // Запускаем авто-атаку
            _player.getAI().tryToAttack(target);
            _player.sendPacket(new AutoAttackStart(_player.getObjectId()));
            
            // Увеличиваем счетчик убитых мобов
            _player.incrementMobsKilled();
        }
        
        /**
         * Авто-подбор лута
         */
        private void autoLoot()
        {
            for (ItemInstance item : World.getInstance().getAroundItems(_player))
            {
                if (_player.isIn3DRadius(item, 150)) && 
                    !item.isEquipped() && 
                    item.getItemLocation() == ItemLocation.VOID)
                {
                    // Подбираем лут
                    _player.getAI().tryToPickUp(item, false);
                    _player.incrementItemsLooted();
                    break; // Подбираем по одному предмету за раз
                }
            }
        }
        
        /**
         * Авто-хил
         */
        private void autoHeal()
        {
            // Хилим если HP меньше 50%
            if (_player.getStatus().getHp() < _player.getStatus().getMaxHp() * 0.5)
            {
                // Используем скилл хила если есть
                useHealSkill();
            }
            
            // Восстанавливаем MP если меньше 30%
            if (_player.getStatus().getMp() < _player.getStatus().getMaxMp() * 0.3)
            {
                // Используем скилл восстановления MP если есть
                useManaSkill();
            }
        }
        
        private void useHealSkill()
        {
            // TODO: Реализовать использование скиллов хила
            // Пока просто отправляем сообщение
            if (_player.getCurrentHp() < _player.getMaxHp() * 0.3)
                _player.sendMessage("❤️ Низкое HP! Используйте хиллинг.");
        }
        
        private void useManaSkill()
        {
            // TODO: Реализовать использование скиллов восстановления MP
        }
    }
}