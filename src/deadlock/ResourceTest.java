
package deadlock;

import deadlock.model.Resource;

public class ResourceTest {

    public static void main(String[] args) {
        Resource r1 = new Resource("R1", 1);

        System.out.println("Initial state: " + r1);

        System.out.println(
            "First allocation: " + r1.allocateInstance()
        );

        System.out.println(
            "Second allocation: " + r1.allocateInstance()
        );

        System.out.println("After allocation: " + r1);

        r1.releaseInstance();

        System.out.println("After release: " + r1);
    }
}
