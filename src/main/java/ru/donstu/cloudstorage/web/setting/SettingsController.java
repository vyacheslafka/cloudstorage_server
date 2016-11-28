package ru.donstu.cloudstorage.web.setting;

import com.sun.xml.internal.bind.v2.TODO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import ru.donstu.cloudstorage.domain.account.entity.Account;
import ru.donstu.cloudstorage.service.account.AccountService;
import ru.donstu.cloudstorage.service.security.SecurityService;
import ru.donstu.cloudstorage.validator.EmailValidator;
import ru.donstu.cloudstorage.validator.PasswordValidator;

import static ru.donstu.cloudstorage.web.cloud.CloudController.REDIRECT_CLOUD;
import static ru.donstu.cloudstorage.web.login.LoginController.REDIRECT_LOGOUT;
import static ru.donstu.cloudstorage.web.setting.SettingsController.ROUTE_SETTINGS;

/**
 * Контроллер страницы настроек аккаунта
 *
 * @author v.solomasov
 */
@Controller
@RequestMapping(ROUTE_SETTINGS)
public class SettingsController {

    public static final String ROUTE_SETTINGS = "/settings";

    public static final String REDIRECT_SETTINGS = "redirect:" + ROUTE_SETTINGS;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private EmailValidator emailValidator;

    @Autowired
    private PasswordValidator passwordValidator;

    @RequestMapping(method = RequestMethod.GET)
    public String settingsPage(Model model) {
        model.addAttribute("isLogged", securityService.isLoggedUser());
        model.addAttribute("account", securityService.getLoggedAccount());
        return "settings";
    }

    @RequestMapping(value = "/name", method = RequestMethod.POST)
    public String settingsName(@RequestParam("name") String name,
                               Model model) {
        Account account = securityService.getLoggedAccount();
        if (accountService.checkAccountName(name)) {
            model.addAttribute("nameError", true);
            return REDIRECT_SETTINGS;
        }
        accountService.updateAccountName(account, name);
        return REDIRECT_CLOUD;
    }

    @RequestMapping(value = "/email", method = RequestMethod.POST)
    public String settingsEmail(@RequestParam("currentEmail") String currentEmail,
                                @RequestParam("newEmail") String newEmail,
                                Model model) {
        Account account = securityService.getLoggedAccount();
        if (!emailValidator.validate(account, currentEmail, newEmail)) {
            model.addAttribute("emailError", true);
            return REDIRECT_SETTINGS;
        }
        accountService.updateAccountEmail(account, newEmail);
        return REDIRECT_CLOUD;
    }

    @RequestMapping(value = "/password", method = RequestMethod.POST)
    public String settingsPassword(@RequestParam("currentPassword") String currentPassword,
                                   @RequestParam("newPassword") String newPassword,
                                   @RequestParam("confirmPassword") String confirmPassword,
                                   Model model) {
        Account account = securityService.getLoggedAccount();
        if (!passwordValidator.validate(account, currentPassword, newPassword, confirmPassword)) {
            model.addAttribute("passwordError", true);
            return REDIRECT_SETTINGS;
        }
        accountService.updateAccountPassword(account, newPassword, confirmPassword);
        return REDIRECT_CLOUD;
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String settingsDelete(@RequestParam("password") String password,
                                 Model model) {
        Account account = securityService.getLoggedAccount();
        /*TODO: Как добавиться SHA, сравнивать хэш-функции*/
        if (!account.getPassword().equals(password)){
            model.addAttribute("deleteError", true);
            return REDIRECT_SETTINGS;
        }
        accountService.deleteAccount(account);
        return REDIRECT_LOGOUT;
    }
}
