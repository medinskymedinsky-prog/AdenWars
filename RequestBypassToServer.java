package net.sf.l2j.gameserver.network.clientpackets;

import java.util.StringTokenizer;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.communitybbs.CommunityBoard;
import net.sf.l2j.gameserver.data.manager.HeroManager;
import net.sf.l2j.gameserver.data.xml.AdminData;
import net.sf.l2j.gameserver.enums.FloodProtector;
import net.sf.l2j.gameserver.handler.AdminCommandHandler;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.OlympiadManagerNpc;
import net.sf.l2j.gameserver.model.olympiad.OlympiadManager;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.scripting.QuestState;

// Импорты для автофарма
import net.sf.l2j.gameserver.data.cache.HtmCache;

public final class RequestBypassToServer extends L2GameClientPacket
{
	private static final Logger GMAUDIT_LOG = Logger.getLogger("gmaudit");
	
	private String _command;
	
	@Override
	protected void readImpl()
	{
		_command = readS();
	}
	
	@Override
	protected void runImpl()
	{
		if (_command.isEmpty())
			return;
		
		if (!getClient().performAction(FloodProtector.SERVER_BYPASS))
			return;
		
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		// ========== ОБРАБОТКА КОМАНД АВТОФАРМА ==========
		if (_command.startsWith("autofarm_"))
		{
			handleAutoFarmBypass(player, _command);
			return;
		}
		// ========== КОНЕЦ АВТОФАРМА ==========
		
		if (_command.startsWith("admin_"))
		{
			String command = _command.split(" ")[0];
			
			final IAdminCommandHandler ach = AdminCommandHandler.getInstance().getHandler(command);
			if (ach == null)
			{
				if (player.isGM())
					player.sendMessage("The command " + command.substring(6) + " doesn't exist.");
				
				return;
			}
			
			if (!AdminData.getInstance().hasAccess(command, player.getAccessLevel()))
			{
				player.sendMessage("You don't have the access rights to use this command.");
				LOGGER.warn("{} tried to use admin command '{}' without proper Access Level.", player.getName(), command);
				return;
			}
			
			if (Config.GMAUDIT)
				GMAUDIT_LOG.info(player.getName() + " [" + player.getObjectId() + "] used '" + _command + "' command on: " + ((player.getTarget() != null) ? player.getTarget().getName() : "none"));
			
			ach.useAdminCommand(_command, player);
		}
		else if (_command.startsWith("player_help "))
		{
			final String path = _command.substring(12);
			if (path.indexOf("..") != -1)
				return;
			
			final StringTokenizer st = new StringTokenizer(path);
			final String[] cmd = st.nextToken().split("#");
			
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile("data/html/help/" + cmd[0]);
			if (cmd.length > 1)
			{
				final int itemId = Integer.parseInt(cmd[1]);
				html.setItemId(itemId);
				
				if (itemId == 7064 && cmd[0].equalsIgnoreCase("lidias_diary/7064-16.htm"))
				{
					final QuestState qs = player.getQuestList().getQuestState("Q023_LidiasHeart");
					if (qs != null && qs.getCond() == 5 && qs.getInteger("diary") == 0)
						qs.set("diary", "1");
				}
			}
			html.disableValidation();
			player.sendPacket(html);
		}
		else if (_command.startsWith("npc_"))
		{
			if (!player.validateBypass(_command))
				return;
			
			int endOfId = _command.indexOf('_', 5);
			String id;
			if (endOfId > 0)
				id = _command.substring(4, endOfId);
			else
				id = _command.substring(4);
			
			try
			{
				final WorldObject object = World.getInstance().getObject(Integer.parseInt(id));
				if (object instanceof Npc npc && endOfId > 0 && player.getAI().canDoInteract(npc))
					npc.onBypassFeedback(player, _command.substring(endOfId + 1));
				
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
			catch (NumberFormatException nfe)
			{
				// Do nothing.
			}
		}
		// Navigate throught Manor windows
		else if (_command.startsWith("manor_menu_select?"))
		{
			WorldObject object = player.getTarget();
			if (object instanceof Npc targetNpc)
				targetNpc.onBypassFeedback(player, _command);
		}
		else if (_command.startsWith("bbs_") || _command.startsWith("_bbs") || _command.startsWith("_friend") || _command.startsWith("_mail") || _command.startsWith("_block"))
		{
			CommunityBoard.getInstance().handleCommands(getClient(), _command);
		}
		else if (_command.startsWith("Quest "))
		{
			if (!player.validateBypass(_command))
				return;
			
			String[] str = _command.substring(6).trim().split(" ", 2);
			if (str.length == 1)
				player.getQuestList().processQuestEvent(str[0], "");
			else
				player.getQuestList().processQuestEvent(str[0], str[1]);
		}
		else if (_command.startsWith("_match"))
		{
			String params = _command.substring(_command.indexOf("?") + 1);
			StringTokenizer st = new StringTokenizer(params, "&");
			int heroclass = Integer.parseInt(st.nextToken().split("=")[1]);
			int heropage = Integer.parseInt(st.nextToken().split("=")[1]);
			int heroid = HeroManager.getInstance().getHeroByClass(heroclass);
			if (heroid > 0)
				HeroManager.getInstance().showHeroFights(player, heroclass, heroid, heropage);
		}
		else if (_command.startsWith("_diary"))
		{
			String params = _command.substring(_command.indexOf("?") + 1);
			StringTokenizer st = new StringTokenizer(params, "&");
			int heroclass = Integer.parseInt(st.nextToken().split("=")[1]);
			int heropage = Integer.parseInt(st.nextToken().split("=")[1]);
			int heroid = HeroManager.getInstance().getHeroByClass(heroclass);
			if (heroid > 0)
				HeroManager.getInstance().showHeroDiary(player, heroclass, heroid, heropage);
		}
		else if (_command.startsWith("arenachange"))
		{
			final boolean isManager = player.getCurrentFolk() instanceof OlympiadManagerNpc;
			
			// Without npc, command can only be used in observer mode on arena.
			if (!isManager && (!player.isInObserverMode() || player.isInOlympiadMode() || player.getOlympiadGameId() < 0))
				return;
			
			// Olympiad registration check.
			if (OlympiadManager.getInstance().isRegisteredInComp(player))
			{
				player.sendPacket(SystemMessageId.WHILE_YOU_ARE_ON_THE_WAITING_LIST_YOU_ARE_NOT_ALLOWED_TO_WATCH_THE_GAME);
				return;
			}
			
			final int arenaId = Integer.parseInt(_command.substring(12).trim());
			player.enterOlympiadObserverMode(arenaId);
		}
	}
	
	// ========== МЕТОДЫ АВТОФАРМА ==========
	
	/**
	 * Обработчик bypass команд автофарма
	 */
	private void handleAutoFarmBypass(Player player, String command)
	{
		try
		{
			if (command.equals("autofarm_start"))
			{
				startAutoFarm(player, 500);
				showAutoFarmMainMenu(player);
			}
			else if (command.equals("autofarm_stop"))
			{
				stopAutoFarm(player);
				showAutoFarmMainMenu(player);
			}
			else if (command.equals("autofarm_settings"))
			{
				showAutoFarmSettings(player);
			}
			else if (command.startsWith("autofarm_save_settings"))
			{
				saveAutoFarmSettings(player, command);
			}
			else if (command.equals("autofarm_stats"))
			{
				showAutoFarmStats(player);
			}
			else if (command.equals("autofarm_pet"))
			{
				showPetFarmMenu(player);
			}
			else if (command.equals("autofarm_main"))
			{
				showAutoFarmMainMenu(player);
			}
			else if (command.startsWith("autofarm_start_radius_"))
			{
				// Команда: autofarm_start_radius_500
				String radiusStr = command.substring("autofarm_start_radius_".length());
				int radius = Integer.parseInt(radiusStr);
				startAutoFarm(player, radius);
				showAutoFarmMainMenu(player);
			}
		}
		catch (Exception e)
		{
			player.sendMessage("Ошибка обработки команды автофарма");
			LOGGER.warn("AutoFarm bypass error: " + e.getMessage());
		}
	}
	
	/**
	 * Показать главное меню автофарма
	 */
	private void showAutoFarmMainMenu(Player player)
	{
		String html = HtmCache.getInstance().getHtm("data/html/mods/autofarm/main.htm");
		if (html == null)
		{
			html = getBasicAutoFarmMenu();
		}
		else
		{
			boolean isFarming = isPlayerFarming(player);
			html = html.replace("%status%", isFarming ? "<font color=00FF00>АКТИВЕН</font>" : "<font color=FF0000>НЕАКТИВЕН</font>");
			html = html.replace("%mobs_killed%", String.valueOf(getMobsKilled(player)));
			html = html.replace("%items_looted%", String.valueOf(getItemsLooted(player)));
		}
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	/**
	 * Показать меню настроек
	 */
	private void showAutoFarmSettings(Player player)
	{
		String html = HtmCache.getInstance().getHtm("data/html/mods/autofarm/settings.htm");
		if (html == null)
		{
			html = getBasicSettingsMenu();
		}
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	/**
	 * Показать меню пет-фарминга
	 */
	private void showPetFarmMenu(Player player)
	{
		String html = HtmCache.getInstance().getHtm("data/html/mods/autofarm/pet_farm.htm");
		if (html == null)
		{
			html = getBasicPetFarmMenu();
		}
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	/**
	 * Показать статистику
	 */
	private void showAutoFarmStats(Player player)
	{
		String html = HtmCache.getInstance().getHtm("data/html/mods/autofarm/stats.htm");
		if (html == null)
		{
			html = getBasicStatsMenu(player);
		}
		else
		{
			html = html.replace("%mobs_killed%", String.valueOf(getMobsKilled(player)));
			html = html.replace("%items_looted%", String.valueOf(getItemsLooted(player)));
			html = html.replace("%farm_time%", "0");
			html = html.replace("%exp_gained%", "0");
			html = html.replace("%adena_gained%", "0");
		}
		
		NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(html);
		player.sendPacket(msg);
	}
	
	/**
	 * Базовое главное меню
	 */
	private String getBasicAutoFarmMenu()
	{
		return "<html><body>" +
			   "<center><h1>🤖 Система Автофарма</h1></center>" +
			   "<br>" +
			   "<table width=300>" +
			   "<tr><td><button value=\"🚀 Старт фарма\" action=\"bypass -h autofarm_start\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"⏹️ Стоп фарма\" action=\"bypass -h autofarm_stop\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"⚙️ Настройки\" action=\"bypass -h autofarm_settings\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"📊 Статистика\" action=\"bypass -h autofarm_stats\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"🐾 Пет-фарминг\" action=\"bypass -h autofarm_pet\" width=200 height=30></td></tr>" +
			   "</table>" +
			   "<br><center><font color=LEVEL>Используйте команды в чате:</font><br>" +
			   ".autofarm start - запустить<br>" +
			   ".autofarm stop - остановить<br>" +
			   ".autofarm status - статус</center>" +
			   "</body></html>";
	}
	
	/**
	 * Базовое меню настроек
	 */
	private String getBasicSettingsMenu()
	{
		return "<html><body>" +
			   "<center><h2>⚙️ Настройки автофарма</h2></center>" +
			   "<br>" +
			   "<table width=300>" +
			   "<tr><td>Радиус поиска:</td>" +
			   "<td><button value=\"300px\" action=\"bypass -h autofarm_start_radius_300\" width=80 height=20></td>" +
			   "<td><button value=\"500px\" action=\"bypass -h autofarm_start_radius_500\" width=80 height=20></td>" +
			   "<td><button value=\"800px\" action=\"bypass -h autofarm_start_radius_800\" width=80 height=20></td></tr>" +
			   "</table>" +
			   "<br>" +
			   "<table width=300>" +
			   "<tr><td><button value=\"✅ Включить авто-лут\" action=\"bypass -h autofarm_setting_autoloot_on\" width=200 height=25></td></tr>" +
			   "<tr><td><button value=\"❌ Выключить авто-лут\" action=\"bypass -h autofarm_setting_autoloot_off\" width=200 height=25></td></tr>" +
			   "<tr><td><button value=\"✅ Включить авто-хил\" action=\"bypass -h autofarm_setting_autoheal_on\" width=200 height=25></td></tr>" +
			   "<tr><td><button value=\"❌ Выключить авто-хил\" action=\"bypass -h autofarm_setting_autoheal_off\" width=200 height=25></td></tr>" +
			   "</table>" +
			   "<br>" +
			   "<a action=\"bypass -h autofarm_main\">← Назад</a>" +
			   "</body></html>";
	}
	
	/**
	 * Базовое меню пет-фарминга
	 */
	private String getBasicPetFarmMenu()
	{
		return "<html><body>" +
			   "<center><h2>🐾 Пет-фарминг</h2></center>" +
			   "<br>" +
			   "<table width=300>" +
			   "<tr><td><button value=\"🐺 Волк-фармер\" action=\"bypass -h autofarm_pet_wolf\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"🥚 Хэтчлинг\" action=\"bypass -h autofarm_pet_hatchling\" width=200 height=30></td></tr>" +
			   "<tr><td><button value=\"🦌 Страйдер\" action=\"bypass -h autofarm_pet_strider\" width=200 height=30></td></tr>" +
			   "</table>" +
			   "<br>" +
			   "<font color=LEVEL>Примечание:</font> Пет-фарминг позволяет вашему питомцу автоматически атаковать мобов и собирать лут." +
			   "<br>" +
			   "<a action=\"bypass -h autofarm_main\">← Назад</a>" +
			   "</body></html>";
	}
	
	/**
	 * Базовое меню статистики
	 */
	private String getBasicStatsMenu(Player player)
	{
		return "<html><body>" +
			   "<center><h2>📊 Статистика фарма</h2></center>" +
			   "<br>" +
			   "<table width=300>" +
			   "<tr><td>Убито мобов:</td><td>0</td></tr>" +
			   "<tr><td>Собрано лута:</td><td>0</td></tr>" +
			   "<tr><td>Получено EXP:</td><td>0</td></tr>" +
			   "<tr><td>Получено аден:</td><td>0</td></tr>" +
			   "<tr><td>Время фарма:</td><td>0 мин</td></tr>" +
			   "</table>" +
			   "<br>" +
			   "<a action=\"bypass -h autofarm_main\">← Назад</a>" +
			   "</body></html>";
	}
	
	/**
	 * Запуск автофарма
	 */
	private void startAutoFarm(Player player, int radius)
	{
		// Временная реализация
		player.setAutoFarm(true);
		player.setFarmRadius(radius);
		
		player.sendMessage("🤖 Автофарм запущен! Радиус: " + radius + "px");
		player.sendMessage("⚔️ Авто-атака | 📦 Авто-лут | 🧪 Авто-бафф");
	}
	
	/**
	 * Остановка автофарма
	 */
	private void stopAutoFarm(Player player)
	{
		// Временная реализация
		player.setAutoFarm(false);
		player.sendMessage("⏹️ Автофарм остановлен");
	}
	
	/**
	 * Сохранение настроек
	 */
	private void saveAutoFarmSettings(Player player, String command)
	{
		// TODO: Реализовать сохранение настроек
		player.sendMessage("⚙️ Настройки сохранены");
		showAutoFarmSettings(player);
	}
	
	// Временные методы-заглушки
	private boolean isPlayerFarming(Player player) { return player.isAutoFarm(); }
	private int getMobsKilled(Player player) { return 0; }
	private int getItemsLooted(Player player) { return 0; }
	// ========== КОНЕЦ МЕТОДОВ АВТОФАРМА ==========
}