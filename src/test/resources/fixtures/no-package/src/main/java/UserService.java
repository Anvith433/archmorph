public class UserService {
    private final UserRepository repository = new UserRepository();

    public int count() {
        return repository.findAll().size();
    }
}
