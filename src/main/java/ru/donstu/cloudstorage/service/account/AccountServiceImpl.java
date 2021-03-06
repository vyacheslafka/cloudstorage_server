package ru.donstu.cloudstorage.service.account;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.donstu.cloudstorage.domain.account.AccountRepository;
import ru.donstu.cloudstorage.domain.account.entity.Account;
import ru.donstu.cloudstorage.domain.account.enums.Role;
import ru.donstu.cloudstorage.domain.userfiles.entity.UserFiles;
import ru.donstu.cloudstorage.service.security.SecurityService;
import ru.donstu.cloudstorage.service.userfiles.UserFilesService;

import java.util.Calendar;
import java.util.List;

/**
 * Реализация интерфейса {@link AccountService}
 *
 * @author v.solomasov
 */
@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger logger = Logger.getLogger(AccountServiceImpl.class);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserFilesService userFilesService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void saveAccount(Account account) {
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        account.setRole(Role.ROLE_USER);
        account.setDataCreate(Calendar.getInstance());
        logger.info(String.format("Зарегистрирован новый пользовательй %s", account.getName()));
        accountRepository.save(account);
    }

    @Override
    public void updateAccountName(Account account, String name, String password) {
        account.setName(name);
        accountRepository.save(account);
        securityService.autoLogin(account.getName(), password);
    }

    @Override
    public void updateAccountEmail(Account account, String email) {
        account.setEmail(email);
        accountRepository.save(account);
    }

    @Override
    public void updateAccountPassword(Account account, String newPassword, String confirmPassword) {
        String oldPassword = account.getPassword();
        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        userFilesService.changeFiles(account, oldPassword);
    }

    @Override
    public void deleteAccount(Account account) {
        List<UserFiles> files = userFilesService.findUserFilesByAccount(account);
        files.stream().forEach(file -> userFilesService.deleteFile(file.getId(), account));
        userFilesService.deleteFolder(account.getId());
        accountRepository.delete(account);
        logger.info(String.format("Пользователь id=%s удален", account.getId()));
    }

    @Override
    public Account findAccountByName(String name) {
        Account account = accountRepository.findByName(name);
        return account;
    }

    @Override
    public boolean checkAccountName(String name) {
        Account account = accountRepository.findByName(name);
        if (account == null) {
            logger.info(String.format("Пользователь с именем %s - не найден", name));
            return false;
        }
        logger.info(String.format("Пользователь с именем %s - найден (id = %d)", name, account.getId()));
        return true;
    }

    @Override
    public boolean checkAccountEmail(String email) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            logger.info(String.format("Пользователь с почтой %s - не найден", email));
            return false;
        }
        logger.info(String.format("Пользователь с почтой %s - найден (id = %d)", email, account.getId()));
        return true;
    }
}
