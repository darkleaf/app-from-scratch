(ns either.core-test
  (:require
   [either.core :as sut]
   [clojure.test :as t]))

(t/deftest step-1
  (t/testing "constructors and extract"
    (t/testing "with value"
      (let [val :val
            l   (sut/left val)
            r   (sut/right val)]
        (t/is (= val
                 (sut/extract l)
                 (sut/extract r)))))
    (t/testing "without value"
      (let [l (sut/left)
            r (sut/right)]
        (t/is (= nil
                 (sut/extract l)
                 (sut/extract r)))))
    (t/testing "default right"
      (t/is (sut/right? 1))
      (t/is (sut/right? "str"))
      (t/is (sut/right? []))
      (t/is (sut/right? nil))))
  (t/testing "print"
    (let [l (sut/left)]
      (t/is (= "#<Left nil>" (pr-str l)))))
  (t/testing ".equals"
    (t/is (= (sut/right)
             (sut/right)))
    (t/is (= (sut/left)
             (sut/left)))
    (t/is (= (sut/right 1)
             (sut/right 1)))
    (t/is (= (sut/left 1)
             (sut/left 1)))
    (t/is (not= (sut/left)
                (sut/right)))
    (t/is (not= (sut/left 1)
                (sut/right 1)))
    (t/is (not= (sut/left)
                nil)))
  (t/testing "hashcode"
    (t/is (= 1 (count (set [(sut/right) (sut/right)]))))
    (t/is (= 1 (count (set [(sut/right 1) (sut/right 1)]))))

    (t/is (= 1 (count (set [(sut/left) (sut/left)]))))
    (t/is (= 1 (count (set [(sut/left 1) (sut/left 1)]))))

    (t/is (= 2 (count (set [(sut/left) (sut/right)]))))
    (t/is (= 2 (count (set [(sut/left 1) (sut/right 1)])))))
  (t/testing "predicates"
    (t/testing "left?"
      (t/is (sut/left? (sut/left)))
      (t/is (not (sut/left? (sut/right)))))
    (t/testing "right?"
      (t/is (sut/right? (sut/right)))
      (t/is (not (sut/right? (sut/left))))))
  (t/testing "invert"
    (let [val :val]
      (t/is (= (sut/left val)
               (sut/invert (sut/right val))))
      (t/is (= (sut/right val)
               (sut/invert (sut/left val))))))
  (t/testing "bimap"
    (t/is (= (sut/left 1)
             (->> 0 sut/left (sut/bimap inc identity))))
    (t/is (= (sut/right 1)
             (->> 0 sut/right (sut/bimap identity inc)))))
  (t/testing "map-left"
    (t/is (= (sut/left 1)
             (->> 0 sut/left (sut/map-left inc))))
    (t/is (= (sut/right 0)
             (->> 0 sut/right (sut/map-left inc)))))
  (t/testing "map-right"
    (t/is (= (sut/left 0)
             (->> 0 sut/left (sut/map-right inc))))
    (t/is (= (sut/right 1)
             (->> 0 sut/right (sut/map-right inc))))))

(t/deftest step-2
  (t/testing "let="
    (t/testing "right"
      (let [ret (sut/let= [x (sut/right 1)
                           y 2]
                  (+ x y))]
        (t/is (= (sut/right 3)
                 ret))))
    (t/testing "left"
      (let [ret (sut/let= [x (sut/left 1)
                           y (sut/right 2)]
                  (sut/right (+ x y)))]
        (t/is (= (sut/left 1)
                 ret))))
    (t/testing "computation"
      (t/testing "right"
        (let [effect-spy   (promise)
              side-effect! (fn [] (deliver effect-spy :ok))]
          (sut/let= [x (sut/right 1)
                     y (sut/right 2)]
            (side-effect!)
            (sut/right (+ x y)))
          (t/is (realized? effect-spy))))
      (t/testing "left"
        (let [y-spy        (promise)
              effect-spy   (promise)
              side-effect! (fn [] (deliver effect-spy :ok))]
          (sut/let= [x (sut/left 1)
                     _ (deliver y-spy :ok)]
            (side-effect!))
          (t/is (not (realized? y-spy)))
          (t/is (not (realized? effect-spy))))))
    (t/testing "destructuring"
      (let [ret (sut/let= [[x y] (sut/right [1 2])]
                  (+ x y))]
        (t/is (= (sut/right 3)
                 ret))))))

(t/deftest step-3
  (t/testing ">>="
    (t/testing "right rights"
      (let [mv   (sut/right 0)
            inc= (comp sut/right inc)
            str= (comp sut/right str)
            ret  (sut/>>= mv inc= str=)]
        (t/is (= (sut/right "1")
                 ret))))
    (t/testing "left right"
      (let [mv   (sut/left 0)
            inc= (comp sut/right inc)
            ret  (sut/>>= mv inc=)]
        (t/is (= (sut/left 0)
                 ret))))
    (t/testing "right lefts"
      (let [mv   (sut/right 0)
            fail= (fn [_] (sut/left :error))
            ret  (sut/>>= mv fail=)]
        (t/is (= (sut/left :error)
                 ret)))))
  (t/testing ">>"
    (t/testing "rights"
      (let [ret (sut/>> (sut/right 1)
                        2)]
        (t/is (= (sut/right 2)
                 ret))))
    (t/testing "lefts"
      (let [spy (promise)
            ret (sut/>> (sut/left 1)
                        (deliver spy :ok))]
        (t/is (= (sut/left 1)
                 ret))
        (t/is (not (realized? spy)))))))
