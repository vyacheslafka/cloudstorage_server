package ru.donstu.cloudstorage.service.userfiles;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.donstu.cloudstorage.domain.account.entity.Account;
import ru.donstu.cloudstorage.domain.userfiles.UserFilesRepository;
import ru.donstu.cloudstorage.domain.userfiles.entity.UserFiles;
import ru.donstu.cloudstorage.exception.AesException;
import ru.donstu.cloudstorage.service.security.SecurityService;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.List;

import static ru.donstu.cloudstorage.config.constant.Constants.RESOURCES_PROPERTY;

/**
 * @author v.solomasov
 *
 * Реализация интерфейса {@link UserFilesService}
 */
@Service
@PropertySource(RESOURCES_PROPERTY)
public class UserFilesServiceImpl implements UserFilesService {

    private static final Logger logger = Logger.getLogger(UserFilesServiceImpl.class);

    private static final String SEPARATOR = File.separator;

    private static final String MIME_TYPE = "application/octet-stream";

    private static final String HEADER_TYPE = "Content-Disposition";

    private static final String USER_FILES = "win.user_files";

    private static final int DIVIDE_MEGABYTE = 1000000;

    private static final int POINT_OFFSET = 2;

    @Autowired
    private UserFilesRepository filesRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private SecurityService securityService;

    @Override
    public void uploadFile(Account account, MultipartFile file) {
        String fullNameFile = file.getOriginalFilename();
        String path = environment.getRequiredProperty(USER_FILES) + SEPARATOR + account.getId();
        try {
            saveFileOnDisk(file, path);
            saveInfoFile(account, fullNameFile, file.getSize(), path);
            logger.info(String.format("Файл %s загружен", fullNameFile));
        } catch (FileNotFoundException e) {
            logger.info(String.format("Файл %s%s%s не найден", path, SEPARATOR, fullNameFile));
        } catch (IOException e) {
            logger.info(String.format("Ошибка загрузки файла %s", fullNameFile));
        }
    }

    @Override
    public List<UserFiles> findUserFilesByAccount(Account account) {
        return filesRepository.findByAccount(account);
    }

    @Override
    public boolean checkUserFile(Account account, String fileName) {
        UserFiles file = filesRepository.findByAccountAndFileName(account, fileName);
        if (file == null) {
            return false;
        }
        return true;
    }

    @Override
    public void deleteFile(Long id, Account account) {
        UserFiles userFiles = filesRepository.findByIdAndAccount(id, account);
        if (userFiles != null) {
            try {
                filesRepository.delete(id);
                deleteFileFromDisk(userFiles.getFilePath(), userFiles.getFileName());
                logger.info(String.format("Файл id=%d, удален пользователем, %s", id, account.getName()));
            } catch (FileNotFoundException e) {
                logger.info(String.format("Файл %s не найден на диске, но удален из БД", userFiles.getFileName()));
            }
        } else {
            logger.info(String.format("Пользователь %s пытался манипулировать с файлом id=%d, которого не существует или принадлежит не ему", account.getName(), id));
        }
    }

    @Override
    public void deleteFolder(Long id) {
        String pathToFolder = environment.getRequiredProperty(USER_FILES) + SEPARATOR + id;
        File file = new File(pathToFolder);
        if (file.delete()) {
            logger.info(String.format("Папка %s удалена", pathToFolder));
        } else {
            logger.info(String.format("Папка %s не найдена", pathToFolder));
        }
    }

    @Override
    public void downloadFile(Long id, Account account, HttpServletResponse response) {
        UserFiles userFiles = filesRepository.findByIdAndAccount(id, account);
        if (userFiles != null) {
            try {
                byte[] buffer = getBytesFile(userFiles);
                String valueHeader = String.format("attachment; filename=%s", userFiles.getFileName());
                response.setContentType(MIME_TYPE);
                response.setHeader(HEADER_TYPE, valueHeader);
                OutputStream outputStream = response.getOutputStream();
                buffer = securityService.decryption(buffer, account.getPassword());
                outputStream.write(buffer, 0, buffer.length);
                outputStream.flush();
                outputStream.close();
            } catch (FileNotFoundException e) {
                logger.info(String.format("Файл %s не найден на диске", userFiles.getFileName()));
            } catch (IOException e) {
                logger.info(String.format("Ошибка загрузки файла %s", userFiles.getFileName()));
            }
        } else {
            logger.info(String.format("Пользователь %s пытался манипуляровать с файлом id=%d, которого не существует или принадлежит не ему", account.getName(), id));
        }
    }

    @Override
    public void changeFiles(Account account, String oldPassword) {
        List<UserFiles> userFiles = findUserFilesByAccount(account);
        userFiles.stream().forEach(userFile -> {
            byte[] decryptedFile = securityService.decryption(getBytesFile(userFile), oldPassword);
            writeFileOnDisk(userFile.getFilePath() + SEPARATOR + userFile.getFileName(), securityService.encryption(decryptedFile));
        });
    }

    /**
     * Получение массива байт файла
     *
     * @param userFiles
     * @return
     */
    private byte[] getBytesFile(UserFiles userFiles) {
        try {
            File file = new File(userFiles.getFilePath() + SEPARATOR + userFiles.getFileName());
            FileInputStream inputStream = new FileInputStream(file);
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            return buffer;
        } catch (IOException e) {
            throw new AesException();
        }
    }

    /**
     * Удаление файла с диска
     *
     * @param path
     * @param name
     * @throws FileNotFoundException
     */
    private void deleteFileFromDisk(String path, String name) throws FileNotFoundException {
        File file = new File(path + SEPARATOR + name);
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        file.delete();
    }

    /**
     * Подготовка {@link MultipartFile} к сохранению
     *
     * @param file
     * @param path
     * @throws IOException
     */
    private void saveFileOnDisk(MultipartFile file, String path) throws IOException {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fullPath = dir.getAbsolutePath() + SEPARATOR + file.getOriginalFilename();
        byte[] bytes = securityService.encryption(file.getBytes());
        writeFileOnDisk(fullPath, bytes);
    }

    /**
     * Сохранение на диск
     *
     * @param fullPath
     * @param buffer
     */
    private void writeFileOnDisk(String fullPath, byte[] buffer) {
        try {
            File serverFile = new File(fullPath);
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(serverFile));
            outputStream.write(buffer);
            outputStream.close();
        } catch (IOException e) {
            logger.info(String.format("Ошибка сохранения файла %s", fullPath));
        }
    }

    /**
     * Создание информации в базе о загруженном файле на сервер
     *
     * @param account
     * @param fileName
     */
    private void saveInfoFile(Account account, String fileName, Long size, String path) {
        UserFiles userFiles = new UserFiles();
        userFiles.setAccount(account);
        userFiles.setFileName(fileName);
        userFiles.setFileLength(size);
        userFiles.setFileLengthShow(new BigDecimal(size).divide(new BigDecimal(DIVIDE_MEGABYTE), POINT_OFFSET, RoundingMode.CEILING));
        userFiles.setFilePath(path);
        userFiles.setDateUpload(Calendar.getInstance());
        filesRepository.save(userFiles);
    }
}
