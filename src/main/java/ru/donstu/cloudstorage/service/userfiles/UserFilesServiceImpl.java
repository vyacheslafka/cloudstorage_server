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

import java.io.*;
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

    @Autowired
    private UserFilesRepository filesRepository;

    @Autowired
    private Environment environment;

    @Override
    public void uploadFile(Account account, MultipartFile file) {
        String fullNameFile = file.getOriginalFilename();
        String path = environment.getRequiredProperty("win.user_files") + SEPARATOR + account.getId();
        try {
            saveFileOnDisk(file, path, fullNameFile);
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
                logger.info(String.format("Файл с id=%d, удален пользователем, %s", id, account.getName()));
            } catch (FileNotFoundException e) {
                logger.info(String.format("Файл %s не найден на диске, но удален из БД", userFiles.getFileName()));
            }
        } else {
            logger.info(String.format("Пользователь %s пытался удалить файл с id=%d, которого не существует или принадлежит не ему", account.getName(), id));
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
        if (!file.exists()){
            throw new FileNotFoundException();
        }
        file.delete();
    }

    /**
     * Сохранение файла на диск
     *
     * @param file
     * @param path
     * @param fullNameFile
     * @throws IOException
     */
    private void saveFileOnDisk(MultipartFile file, String path, String fullNameFile) throws IOException {
        byte[] bytes = file.getBytes();
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File serverFile = new File(dir.getAbsolutePath() + SEPARATOR + fullNameFile);
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(serverFile));
        outputStream.write(bytes);
        outputStream.close();
    }

    /**
     * Создание информации в базе о загруженном файле на сервер
     *
     * @param account
     * @param fileName
     */
    private void saveInfoFile(Account account, String fileName, Long size, String path){
        UserFiles userFiles = new UserFiles();
        userFiles.setAccount(account);
        userFiles.setFileName(fileName);
        userFiles.setFileLength(size);
        userFiles.setFilePath(path);
        userFiles.setDateUpload(Calendar.getInstance());
        filesRepository.save(userFiles);
    }
}
